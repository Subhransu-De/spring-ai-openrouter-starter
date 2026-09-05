package de.subhransu.openrouter.springai.embedding.mapper;

import de.subhransu.openrouter.springai.api.dto.EmbeddingsResponse;
import de.subhransu.openrouter.springai.api.dto.Usage;
import de.subhransu.openrouter.springai.chat.OpenRouterUsage;
import java.util.List;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.util.CollectionUtils;

public final class OpenRouterEmbeddingResponseMapper {

	public EmbeddingResponse map(EmbeddingsResponse response) {
		List<Embedding> embeddings = CollectionUtils.isEmpty(response.data()) ? List.of()
				: response.data().stream().map(data -> new Embedding(data.embedding(), data.index())).toList();
		return new EmbeddingResponse(embeddings, mapMetadata(response));
	}

	private EmbeddingResponseMetadata mapMetadata(EmbeddingsResponse response) {
		Usage usage = response.usage();
		if (usage == null) {
			return new EmbeddingResponseMetadata(response.model(), new EmptyUsage());
		}
		return new EmbeddingResponseMetadata(response.model(),
				new OpenRouterUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens(),
						usage.cachedTokens(), usage.reasoningTokens(), usage.cost(), usage));
	}

}
