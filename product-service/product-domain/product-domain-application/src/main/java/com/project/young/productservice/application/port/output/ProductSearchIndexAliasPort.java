package com.project.young.productservice.application.port.output;

import com.project.young.productservice.application.port.output.ProductSearchIndexActivationWorkPort.Work;

import com.project.young.productservice.application.searchindex.model.ProductSearchIndexAliasState;

public interface ProductSearchIndexAliasPort {
    ProductSearchIndexAliasState readAlias(String aliasName);

    void verifyBeforeSwitch(Work work);

    void switchAlias(Work work);

    void verifyAfterSwitch(Work work);
}
