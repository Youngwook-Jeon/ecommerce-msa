package com.project.young.productservice.domain.valueobject;

import com.project.young.common.domain.valueobject.AttributeDefinitionId;
import com.project.young.common.domain.valueobject.OptionGroupId;

/** Non-locking parent lookup. A null attributeId denotes a legacy unlinked group. */
public record OptionGroupAttributeBinding(OptionGroupId groupId, AttributeDefinitionId attributeId) {
}
