package de.subhransu.openrouter.springai.chat.mapper;

import java.util.Base64;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeType;

/**
 * Derives the OpenRouter {@code image_url} value from Spring AI {@link Media}: URI-backed
 * media pass through as plain URLs, byte-backed media become {@code data:} base64 URLs.
 *
 * @author Subhransu De
 */
final class MediaUrlMapper {

	private MediaUrlMapper() {
	}

	static String imageUrl(Media media) {
		MimeType mimeType = media.getMimeType();
		// Video, audio and PDF inputs use different content-part shapes (video_url,
		// input_audio, file) and are not mapped yet; failing loudly beats silently
		// dropping an attachment from the request.
		if (mimeType == null || !"image".equals(mimeType.getType())) {
			throw new IllegalArgumentException("Unsupported media mime type '" + mimeType
					+ "': only image media is currently mapped to OpenRouter content parts");
		}
		Object data = media.getData();
		if (data instanceof String url) {
			return url;
		}
		if (data instanceof java.net.URI || data instanceof java.net.URL) {
			return data.toString();
		}
		return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(media.getDataAsByteArray());
	}

}
