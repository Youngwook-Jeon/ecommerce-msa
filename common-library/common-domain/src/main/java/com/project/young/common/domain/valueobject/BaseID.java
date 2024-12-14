package com.project.young.common.domain.valueobject;

import java.util.Objects;

public abstract class BaseID<T> {
    private final T value;

    protected BaseID(T value) {
        this.value = value;
    }

    public T getValue() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseID<?> baseID = (BaseID<?>) o;
        return value.equals(baseID.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
