package com.project.young.productservice.application.port.output;

import com.project.young.productservice.application.port.output.ProductSearchIndexActivationWorkPort.Work;

import java.util.Set;

public interface ProductSearchIndexAliasPort {
    record AliasState(Set<String> indices, boolean plain) {
        public AliasState {
            indices = Set.copyOf(indices);
        }
    }

    AliasState readAlias(String aliasName);

    void verifyBeforeSwitch(Work work);

    void switchAlias(Work work);

    void verifyAfterSwitch(Work work);
}
