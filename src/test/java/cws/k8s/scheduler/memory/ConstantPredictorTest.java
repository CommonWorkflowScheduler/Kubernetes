/*
 * Copyright (c) 2023, Florian Friederici. All rights reserved.
 * 
 * This code is free software: you can redistribute it and/or modify it under 
 * the terms of the GNU General Public License as published by the Free 
 * Software Foundation, either version 3 of the License, or (at your option) 
 * any later version.
 * 
 * This code is distributed in the hope that it will be useful, but WITHOUT ANY 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS 
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more 
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with 
 * this work. If not, see <https://www.gnu.org/licenses/>. 
 */

package cws.k8s.scheduler.memory;

import static org.junit.Assert.*;

import java.math.BigDecimal;

import org.junit.Test;

import cws.k8s.scheduler.model.Task;
import lombok.extern.slf4j.Slf4j;

/**
 * JUnit 4 Tests for the ConstantPredictor
 * 
 * @author Florian Friederici
 * 
 */
@Slf4j
public class ConstantPredictorTest {

    /**
     * If there are no observations, we cannot get a prediction
     * 
     */
    @Test
    public void testNoObservationsYet() {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        ConstantPredictor constantPredictor = new ConstantPredictor();
        Task task = MemoryPredictorTest.createTask("taskName");
        assertNull(constantPredictor.querySuggestion(task));
    }

    /**
     * If there is one observation, we get a prediction
     * 
     */
    @Test
    public void testOneObservation() {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        ConstantPredictor constantPredictor = new ConstantPredictor();
        Task task = MemoryPredictorTest.createTask("taskName");
        // @formatter:off
        Observation observation = Observation.builder()
                .task("taskName")
                .taskName("taskName (1)")
                .success(true)
                .inputSize(0)
                .ramRequest(BigDecimal.valueOf(0))
                .ramLimit(BigDecimal.valueOf(0))
                .peakRss(BigDecimal.valueOf(0))
                .build();
        // @formatter:on
        constantPredictor.addObservation(observation);
        assertNotNull(constantPredictor.querySuggestion(task));
    }

    /**
     * If there are two observations, we will also get a suggestion
     */
    @Test
    public void testTwoObservations() {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        ConstantPredictor constantPredictor = new ConstantPredictor();
        Task task = MemoryPredictorTest.createTask("taskName");
        // @formatter:off
        Observation observation1 = Observation.builder()
                .task("taskName")
                .taskName("taskName (1)")
                .success(true)
                .inputSize(0)
                .ramRequest(BigDecimal.valueOf(0))
                .ramLimit(BigDecimal.valueOf(0))
                .peakRss(BigDecimal.valueOf(0))
                .build();
        Observation observation2 = Observation.builder()
                .task("taskName")
                .taskName("taskName (1)")
                .success(true)
                .inputSize(0)
                .ramRequest(BigDecimal.valueOf(0))
                .ramLimit(BigDecimal.valueOf(0))
                .peakRss(BigDecimal.valueOf(0))
                .build();
        // @formatter:on
        constantPredictor.addObservation(observation1);
        constantPredictor.addObservation(observation2);
        assertNotNull(constantPredictor.querySuggestion(task));
    }

    /**
     * The prediction decreases right after one observation
     * 
     */
    @Test
    public void testDecreasePredictionAfterOneObservation() {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        ConstantPredictor constantPredictor = new ConstantPredictor();
        Task task = MemoryPredictorTest.createTask("taskName");

        BigDecimal reserved = BigDecimal.valueOf(4l * 1024 * 1024 * 1024);
        BigDecimal used = BigDecimal.valueOf(2l * 1024 * 1024 * 1024);

        // @formatter:off
        Observation observation = Observation.builder()
                .task("taskName")
                .taskName("taskName (1)")
                .success(true)
                .inputSize(0)
                .ramRequest(reserved)
                .ramLimit(reserved)
                .peakRss(used)
                .build();
        // @formatter:on
        constantPredictor.addObservation(observation);
        String suggestionStr = constantPredictor.querySuggestion(task);
        log.debug("suggestion is: {}", suggestionStr);
        // 1. There is a suggestion at all
        assertNotNull(suggestionStr);
        BigDecimal suggestion = new BigDecimal(suggestionStr);
        // 2. The suggestion is lower than the reserved value was
        assertTrue(suggestion.compareTo(reserved) < 0);
        // 3. The suggestion is higher than the used value was
        assertTrue(suggestion.compareTo(used) > 0);
    }

    /**
     * The prediction decreases further, if another successful observation is made
     * 
     */
    @Test
    public void testDecreasePredictionAfterMultipleObservations() {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        ConstantPredictor constantPredictor = new ConstantPredictor();

        BigDecimal suggestion1 = MemoryPredictorTest.createTaskObservationSuccessSuggestion(constantPredictor,
                BigDecimal.valueOf(4l * 1024 * 1024 * 1024), BigDecimal.valueOf(2l * 1024 * 1024 * 1024));
        BigDecimal suggestion2 = MemoryPredictorTest.createTaskObservationSuccessSuggestion(constantPredictor,
                BigDecimal.valueOf(4l * 1024 * 1024 * 1024), BigDecimal.valueOf(2l * 1024 * 1024 * 1024));
        assertTrue(suggestion1.compareTo(suggestion2) >= 0);

        BigDecimal suggestion3 = MemoryPredictorTest.createTaskObservationSuccessSuggestion(constantPredictor,
                BigDecimal.valueOf(4l * 1024 * 1024 * 1024), BigDecimal.valueOf(2l * 1024 * 1024 * 1024));
        assertTrue(suggestion2.compareTo(suggestion3) >= 0);

        BigDecimal suggestion4 = MemoryPredictorTest.createTaskObservationSuccessSuggestion(constantPredictor,
                BigDecimal.valueOf(4l * 1024 * 1024 * 1024), BigDecimal.valueOf(2l * 1024 * 1024 * 1024));
        assertTrue(suggestion3.compareTo(suggestion4) >= 0);
    }

    /**
     * When the Task failed, increase the prediction already after one observation
     * 
     */
    @Test
    public void testIncreasePredictionAfterFailure() {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        ConstantPredictor constantPredictor = new ConstantPredictor();
        
        BigDecimal reserved = BigDecimal.valueOf(4l * 1024 * 1024 * 1024);
        BigDecimal used = reserved.add(BigDecimal.ONE);
        
        BigDecimal suggestion = MemoryPredictorTest.createTaskObservationFailureSuggestion(constantPredictor, reserved, used);
        log.info("reserved     : {})", reserved);
        log.info("used         : {})", used);
        log.info("suggestion is: {})", suggestion.toPlainString());
    }

    /**
     * When the Task failed after some successful observations, increase
     * 
     */
    @Test
    public void testIncreasePredictionAfterSuccessAndFailure() {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        ConstantPredictor constantPredictor = new ConstantPredictor();
        
        BigDecimal reserved = BigDecimal.valueOf(4l * 1024 * 1024 * 1024);
        BigDecimal usedSucc = BigDecimal.valueOf(2l * 1024 * 1024 * 1024);
        BigDecimal usedFail = reserved.add(BigDecimal.ONE);
        
        BigDecimal suggestion1 = MemoryPredictorTest.createTaskObservationSuccessSuggestion(constantPredictor, reserved, usedSucc);
        log.info("reserved      : {})", reserved);
        log.info("usedSucc      : {})", usedSucc);
        log.info("suggestion1 is: {})", suggestion1);

        BigDecimal suggestion2 = MemoryPredictorTest.createTaskObservationSuccessSuggestion(constantPredictor, suggestion1, usedSucc);
        log.info("reserved      : {})", suggestion1);
        log.info("usedSucc      : {})", usedSucc);
        log.info("suggestion2 is: {})", suggestion2);

        BigDecimal suggestion3 = MemoryPredictorTest.createTaskObservationFailureSuggestion(constantPredictor, suggestion2, usedFail);
        log.info("reserved      : {})", suggestion2);
        log.info("usedFail      : {})", usedFail);
        log.info("suggestion3 is: {})", suggestion3);

        BigDecimal suggestion4 = MemoryPredictorTest.createTaskObservationSuccessSuggestion(constantPredictor, suggestion3, usedSucc);
        log.info("reserved      : {})", suggestion3);
        log.info("usedSucc      : {})", usedSucc);
        log.info("suggestion4 is: {})", suggestion4);

        BigDecimal suggestion5 = MemoryPredictorTest.createTaskObservationSuccessSuggestion(constantPredictor, suggestion4, usedSucc);
        log.info("reserved      : {})", suggestion4);
        log.info("usedSucc      : {})", usedSucc);
        log.info("suggestion5 is: {})", suggestion5);
    }

}
