package com.project.young.common.domain.util;

import com.project.young.common.domain.valueobject.BaseID;

public interface IdentityGenerator<T extends BaseID<?>> {

    T generateID();
}
