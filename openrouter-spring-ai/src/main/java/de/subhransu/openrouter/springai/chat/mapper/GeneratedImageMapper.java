package de.subhransu.openrouter.springai.chat.mapper;

import de.subhransu.openrouter.springai.api.dto.ContentPart;
import de.subhransu.openrouter.springai.api.dto.ResponsesOutputItem;
import java.util.List;
import org.springframework.ai.content.Media;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

/**
 * Maps the {@code images} array that image-generating chat models attach to assistant
 * messages (and streaming deltas) into Spring AI {@link Media}. Images arrive as
 * {@code image_url} parts whose url is normally a base64 data URL; the data URL string is
 * kept verbatim as the media data so callers can decode or pass it on unchanged.
 *
 * @author Subhransu De
 */
final class GeneratedImageMapper {

	private static final String DATA_URL_PREFIX = "data:";

	private GeneratedImageMapper() {
	}

	static List<Media> media(List<ContentPart> images) {
		if (CollectionUtils.isEmpty(images)) {
			return List.of();
		}
		return images.stream()
			.filter(part -> part.imageUrl() != null && part.imageUrl().url() != null)
			.map(part -> Media.builder().mimeType(mimeType(part.imageUrl().url())).data(part.imageUrl().url()).build())
			.toList();
	}

	// The Responses API returns generated images as image_generation_call output items
	// whose result is the raw base64 image bytes (no data-URL wrapper and no media type);
	// the base64 string is kept verbatim and the mime type defaults to PNG like plain
	// chat image URLs.
	static List<Media> responsesMedia(List<ResponsesOutputItem> output) {
		if (CollectionUtils.isEmpty(output)) {
			return List.of();
		}
		return output.stream()
			.filter(item -> "image_generation_call".equals(item.type()) && item.result() != null)
			.map(item -> Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(item.result()).build())
			.toList();
	}

	private static MimeType mimeType(String url) {
		int end = url.indexOf(';');
		if (!url.startsWith(DATA_URL_PREFIX) || end <= DATA_URL_PREFIX.length()) {
			// The docs describe generated images as "typically PNG"; plain URLs and
			// malformed data URLs carry no better signal.
			return MimeTypeUtils.IMAGE_PNG;
		}
		try {
			return MimeTypeUtils.parseMimeType(url.substring(DATA_URL_PREFIX.length(), end));
		}
		catch (RuntimeException ex) {
			return MimeTypeUtils.IMAGE_PNG;
		}
	}

}
