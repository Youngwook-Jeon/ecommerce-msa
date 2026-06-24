package com.project.young.orderservice.adapter;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import com.project.young.orderservice.application.port.output.IdGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UuidV7Generator implements IdGenerator {

    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    @Override
    public UUID generateId() {
        return GENERATOR.generate();
    }
}
