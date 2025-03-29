package com.project.young.common.domain.util;

import com.project.young.common.domain.valueobject.BaseId;

public interface IdentityGenerator<T extends BaseId<?>> {

    T generateID();
}
