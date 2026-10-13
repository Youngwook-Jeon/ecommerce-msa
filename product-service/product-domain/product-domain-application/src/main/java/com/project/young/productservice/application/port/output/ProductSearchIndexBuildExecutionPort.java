package com.project.young.productservice.application.port.output;

import java.util.function.LongConsumer;

public interface ProductSearchIndexBuildExecutionPort {
    void create(ProductSearchIndexBuildWorkPort.Work work);

    void load(ProductSearchIndexBuildWorkPort.Work work, Runnable checkpoint, LongConsumer completedBatch);

    long verify(ProductSearchIndexBuildWorkPort.Work work, Runnable checkpoint);
}
