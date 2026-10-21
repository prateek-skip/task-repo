package com.example.catalog.application.command;

import java.util.Objects;
import java.util.function.Function;

/**
 * One field of a partial update. JSON Merge Patch gives "absent" and "null" different meanings
 * (keep the value vs. remove it), and a plain nullable cannot tell them apart. Three explicit
 * states can.
 */
public sealed interface PatchField<T> permits PatchField.Unchanged, PatchField.Clear, PatchField.Set {

    record Unchanged<T>() implements PatchField<T> {
    }

    record Clear<T>() implements PatchField<T> {
    }

    record Set<T>(T value) implements PatchField<T> {

        public Set {
            Objects.requireNonNull(value, "value");
        }
    }

    static <T> PatchField<T> unchanged() {
        return new Unchanged<>();
    }

    static <T> PatchField<T> clear() {
        return new Clear<>();
    }

    static <T> PatchField<T> set(T value) {
        return new Set<>(value);
    }

    /** The field's new value given its current one: kept, removed or replaced. */
    default T applyTo(T current) {
        return switch (this) {
            case Unchanged<T> unchanged -> current;
            case Clear<T> clear -> null;
            case Set<T> set -> set.value();
        };
    }

    /** Transforms a set value, e.g. a category slug into the category it names. */
    default <R> PatchField<R> map(Function<? super T, ? extends R> mapper) {
        return switch (this) {
            case Unchanged<T> unchanged -> unchanged();
            case Clear<T> clear -> clear();
            case Set<T> set -> set(mapper.apply(set.value()));
        };
    }
}
