package com.project.young.paymentservice.adapter;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import com.project.young.paymentservice.application.port.output.IdGenerator;
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
