package dev.dosa.typesafe.model;

/**
 * Answer to a {@link NoulQuestion}.
 *
 * @param value the model's probability that the answer is "true", between 0 and 1
 */
public record NoulAnswer(double value) implements Answer {

    @Override
    public String type() {
        return "noul";
    }
}
