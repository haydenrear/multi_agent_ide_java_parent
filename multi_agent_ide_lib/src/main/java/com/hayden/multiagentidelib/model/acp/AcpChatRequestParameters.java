package com.hayden.multiagentidelib.model.acp;

import jakarta.annotation.Nullable;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.util.Arrays.asList;

@Data
@Getter
@Setter
public class AcpChatRequestParameters implements ChatOptions {

    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer topK;
    private final Double frequencyPenalty;
    private final Double presencePenalty;
    private final Integer maxOutputTokens;
    private final List<String> stopSequences;
    private final ResponseFormat responseFormat;
    private final Object memoryId;

    protected AcpChatRequestParameters(Builder<?> builder) {
        this.modelName = builder.modelName;
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.topK = builder.topK;
        this.frequencyPenalty = builder.frequencyPenalty;
        this.presencePenalty = builder.presencePenalty;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.stopSequences = new ArrayList<>(builder.stopSequences);
        this.responseFormat = builder.responseFormat;
        this.memoryId = builder.memoryId;
    }

    public Object memoryId() {
        return memoryId;
    }

    public AcpChatRequestParameters overrideWith(ChatOptions that) {
        return AcpChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AcpChatRequestParameters that = (AcpChatRequestParameters) o;
        return Objects.equals(modelName, that.modelName)
                && Objects.equals(temperature, that.temperature)
                && Objects.equals(topP, that.topP)
                && Objects.equals(topK, that.topK)
                && Objects.equals(frequencyPenalty, that.frequencyPenalty)
                && Objects.equals(presencePenalty, that.presencePenalty)
                && Objects.equals(maxOutputTokens, that.maxOutputTokens)
                && Objects.equals(stopSequences, that.stopSequences)
                && Objects.equals(responseFormat, that.responseFormat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                modelName,
                temperature,
                topP,
                topK,
                frequencyPenalty,
                presencePenalty,
                maxOutputTokens,
                stopSequences,
                responseFormat);
    }

    @Override
    public String toString() {
        return "AcpChatRequestParameters{" + "modelName='"
                + modelName + '\'' + ", temperature="
                + temperature + ", topP="
                + topP + ", topK="
                + topK + ", frequencyPenalty="
                + frequencyPenalty + ", presencePenalty="
                + presencePenalty + ", maxOutputTokens="
                + maxOutputTokens + ", stopSequences="
                + stopSequences + ", responseFormat="
                + responseFormat + '}';
    }

    @Override
    public @Nullable String getModel() {
        return modelName;
    }

    @Override
    public @Nullable Integer getMaxTokens() {
        return maxOutputTokens;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ChatOptions> T copy() {
        return (T) this;
    }

    public static Builder<?> builder() {
        return new Builder<>();
    }

    public static class Builder<T extends Builder<T>> {

        private String modelName;
        private Double temperature;
        private Double topP;
        private Integer topK;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private Integer maxOutputTokens;
        private List<String> stopSequences = new ArrayList<>();
        private ResponseFormat responseFormat;
        private Object memoryId;

        public <U> U getOrDefault(U value, U other) {
            if (value != null) {
                return value;
            }
            return other;
        }

        public T overrideWith(ChatOptions parameters) {
            modelName(getOrDefault(parameters.getModel(), modelName));
            temperature(getOrDefault(parameters.getTemperature(), temperature));
            topP(getOrDefault(parameters.getTopP(), topP));
            topK(getOrDefault(parameters.getTopK(), topK));
            frequencyPenalty(getOrDefault(parameters.getFrequencyPenalty(), frequencyPenalty));
            presencePenalty(getOrDefault(parameters.getPresencePenalty(), presencePenalty));
            maxOutputTokens(getOrDefault(parameters.getMaxTokens(), maxOutputTokens));
            stopSequences(getOrDefault(parameters.getStopSequences(), stopSequences));

            if (parameters instanceof AcpChatRequestParameters p && p.memoryId != null) {
                memoryId(p.memoryId);
            }

            return (T) this;
        }

        public T modelName(String modelName) {
            this.modelName = modelName;
            return (T) this;
        }

        public T temperature(Double temperature) {
            this.temperature = temperature;
            return (T) this;
        }

        public T topP(Double topP) {
            this.topP = topP;
            return (T) this;
        }

        public T topK(Integer topK) {
            this.topK = topK;
            return (T) this;
        }

        public T frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return (T) this;
        }

        public T presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return (T) this;
        }

        public T maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return (T) this;
        }

        public T stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return (T) this;
        }

        public T stopSequences(String... stopSequences) {
            return stopSequences(asList(stopSequences));
        }

        public T responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return (T) this;
        }

        public T memoryId(Object memId) {
            this.memoryId = memId;
            return (T) this;
        }

        public AcpChatRequestParameters build() {
            return new AcpChatRequestParameters(this);
        }
    }
}
