package com.project.young.common.domain.valueobject;

import java.util.Objects;

public abstract class BaseId<T> {
    private final T value;

    protected BaseId(T value) {
        if (value == null) {
            throw new IllegalArgumentException("ID value cannot be null.");
        }
        this.value = value;
    }

    public T getValue() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseId<?> baseID = (BaseId<?>) o;
        return value.equals(baseID.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
