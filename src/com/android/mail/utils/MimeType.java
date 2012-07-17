/**
 * Copyright (c) 2010, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.mail.utils;

import com.android.mail.utils.LogTag;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

import java.util.List;
import java.util.Set;

/**
 * Utilities for working with different content types within Mail.
 */
public class MimeType {
    private static final String LOG_TAG = LogTag.getLogTag();

    public static final String ANDROID_ARCHIVE = "application/vnd.android.package-archive";
    private static final String TEXT_PLAIN = "text/plain";
    @VisibleForTesting
    static final String GENERIC_MIMETYPE = "application/octet-stream";

    @VisibleForTesting
    static final String EML_ATTACHMENT_CONTENT_TYPE = "application/eml";
    private static final String NULL_ATTACHMENT_CONTENT_TYPE = "null";
    private static final Set<String> UNACCEPTABLE_ATTACHMENT_TYPES = ImmutableSet.of(
            "application/zip", "application/x-gzip", "application/x-bzip2",
            "application/x-compress", "application/x-compressed", "application/x-tar");

    private static Set<String> sGviewSupportedTypes = ImmutableSet.of(
            "application/pdf",
            "application/vnd.ms-powerpoint",
            "image/tiff",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");

    /**
     * Returns whether or not an attachment of the specified type is installable (e.g. an apk).
     */
    public static boolean isInstallable(String type) {
        return ANDROID_ARCHIVE.equals(type);
    }

    /**
     * Returns whether or not an attachment of the specified type is playable (e.g. a video).
     */
    public static boolean isPlayable(String type) {
        return !TextUtils.isEmpty(type) ?
                (type.startsWith("video/") || type.startsWith("audio/"))
                : false;
    }

    /**
     * Returns whether or not an attachment of the specified type is viewable.
     */
    public static boolean isViewable(Context context, Uri contentUri, String contentType) {
        // The provider returns a contentType of "null" instead of null, when the
        // content type is not known.  Changing the provider to return null,
        // breaks other areas that will need to be fixed in a later CL.
        // Bug 2922948 has been written up to track this
        if (contentType == null || contentType.length() == 0 ||
                NULL_ATTACHMENT_CONTENT_TYPE.equals(contentType)) {
            LogUtils.d(LOG_TAG, "Attachment with null content type. '%s", contentUri);
            return false;
        }

        if (isBlocked(contentType)) {
            LogUtils.d(LOG_TAG, "content type '%s' is blocked. '%s", contentType, contentUri);
            return false;
        }

        final Intent mimetypeIntent = new Intent(Intent.ACTION_VIEW);
        mimetypeIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

        Utils.setIntentDataAndTypeAndNormalize(mimetypeIntent, contentUri, contentType);

        PackageManager manager;
        // We need to catch the exception to make CanvasConversationHeaderView
        // test pass.  Bug: http://b/issue?id=3470653.
        try {
            manager = context.getPackageManager();
        } catch (UnsupportedOperationException e) {
            return false;
        }
        final List<ResolveInfo> list = manager.queryIntentActivities(mimetypeIntent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    /**
     * @return whether the specified type is blocked.
     */
    public static boolean isBlocked(String contentType) {
        return UNACCEPTABLE_ATTACHMENT_TYPES.contains(contentType);
    }

    /* TODO: what do we want to do about GSF keys for the unified app?
    public static boolean isPreviewable(Context context, String contentType) {
        final String supportedTypes = Gservices.getString(
                context.getContentResolver(), GservicesKeys.GMAIL_GVIEW_SUPPORTED_TYPES);
        if (supportedTypes != null) {
            sGviewSupportedTypes = ImmutableSet.of(TextUtils.split(supportedTypes, ","));
        }
        return sGviewSupportedTypes.contains(contentType);
    }*/

    /**
     * Extract and return filename's extension, converted to lower case, and not including the "."
     *
     * @return extension, or null if not found (or null/empty filename)
     */
    private static String getFilenameExtension(String fileName) {
        String extension = null;
        if (!TextUtils.isEmpty(fileName)) {
            int lastDot = fileName.lastIndexOf('.');
            if ((lastDot > 0) && (lastDot < fileName.length() - 1)) {
                extension = fileName.substring(lastDot + 1).toLowerCase();
            }
        }
        return extension;
    }


    /**
     * Returns the mime type of the attachment based on its name and
     * original mime type. This is an workaround for bugs where Gmail
     * server doesn't set content-type for certain types correctly.
     * 1) EML files -> "application/eml".
     * @param name name of the attachment.
     * @param mimeType original mime type of the attachment.
     * @return the inferred mime type of the attachment.
     */
    public static String inferMimeType(String name, String mimeType) {
        final String extension = getFilenameExtension(name);
        if (TextUtils.isEmpty(extension)) {
            // Attachment doesn't have extension, just return original mime
            // type.
            return mimeType;
        } else {
            final boolean isTextPlain = TEXT_PLAIN.equalsIgnoreCase(mimeType);
            final boolean isGenericType =
                    isTextPlain || GENERIC_MIMETYPE.equalsIgnoreCase(mimeType);

            String type = null;
            if (isGenericType || TextUtils.isEmpty(mimeType)) {
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            }
            if (!TextUtils.isEmpty(type)) {
                return type;
            } if (extension.equals("eml")) {
                // Extension is ".eml", return mime type "application/eml"
                return EML_ATTACHMENT_CONTENT_TYPE;
            } else {
                // Extension is not ".eml", just return original mime type.
                return !TextUtils.isEmpty(mimeType) ? mimeType : GENERIC_MIMETYPE;
            }
        }
    }
}
