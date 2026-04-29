// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ConcurrencyUtilTest
{

    @ParameterizedTest
    @CsvSource
    ({
        "10, 3, 4", // 10 items, batch size 3 -> 4 batches (3,3,3,1)
        "10, 5, 2", // 10 items, batch size 5 -> 2 batches (5,5)
        "10, 10, 1", // 10 items, batch size 10 -> 1 batch
        "10, 20, 1"  // 10 items, batch size 20 -> 1 batch
    })
    @DisplayName("Should split collection into correct number of batches")
    public void splitIntoBatches_ShouldReturnCorrectBatchCount
    (
        int totalSize, 
        int batchSize, 
        int expectedBatches
    ) 
    {
        List<Integer> input = IntStream.range(0, totalSize).boxed().toList();

        List<List<Integer>> result = ConcurrencyUtil.splitIntoBatches(input, batchSize, ArrayList::new);

        assertThat(result).hasSize(expectedBatches);
        result.forEach(batch->assertThat(batch).hasSizeLessThanOrEqualTo(batchSize));
        assertThat(result.stream().flatMap(Collection::stream)).containsExactlyElementsOf(input);
    }

    @Test
    @DisplayName("Should return empty list when input is empty")
    public void splitIntoBatches_EmptyInput()
    {
        List<List<Integer>> result = ConcurrencyUtil.splitIntoBatches(List.of(), 5, ArrayList::new);
        assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("Should throw exception for non-positive batch size")
    public void splitIntoBatches_InvalidBatchSize(int batchSize)
    {
        assertThatThrownBy(()->ConcurrencyUtil.splitIntoBatches(List.of(1), batchSize, ArrayList::new))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Positive batchSize expected");
    }

    @Test
    @DisplayName("batchCall should process all items using multiple threads")
    public void batchCall_AsyncExecution()
    {
        String originalThreadName = Thread.currentThread().getName();
        assertFalse(originalThreadName.startsWith(
            ConcurrencyUtil.THREAD_POOL_TASK_EXECUTOR.getThreadNamePrefix()));
        List<Integer> input = IntStream.range(0, 20).boxed().toList();
        int batchSize = 5;
        List<String> threadNames = new ArrayList<>(4);
        Function<List<Integer>, Integer> sumTask = batch->
        {
            threadNames.add(Thread.currentThread().getName());
            return batch.stream()
                .mapToInt(i->i)
                .sum();
        };

        List<Integer> results = ConcurrencyUtil.batchCall
        (
            input,
            batchSize,
            ArrayList::new,
            sumTask,
            ConcurrencyUtil.THREAD_POOL_TASK_EXECUTOR
        );

        assertThat(results).containsExactly(10, 35, 60, 85);
        assertTrue(threadNames.remove(originalThreadName));
        assertThat(threadNames).hasSize(3)
            .allMatch(s->s.startsWith(ConcurrencyUtil.THREAD_POOL_TASK_EXECUTOR.getThreadNamePrefix()));
    }

    @Test
    @DisplayName("batchCall with DIRECT_EXECUTOR should run in calling thread")
    public void batchCall_DirectExecutor()
    {
        List<Integer> input = List.of(1, 2, 3, 4);
        String originalThreadName = Thread.currentThread().getName();

        List<String> threadNames = ConcurrencyUtil.batchCall
        (
            input,
            1,
            ArrayList::new,
            batch->Thread.currentThread().getName()
        );

        assertThat(threadNames).allMatch(name->name.equals(originalThreadName));
    }

    @Test
    @DisplayName("batchCallFlatMap should flatten batch results into a single list")
    public void batchCallFlatMap_Success()
    {
        List<Integer> input = List.of(1, 2, 3, 4, 5, 6, 7, 8);
        Function<List<Integer>, List<Integer>> multiplyTask = batch->batch.stream()
            .map(i -> i * 10)
            .toList();

        List<Integer> result = ConcurrencyUtil.batchCallFlatMap
        (
            input,
            3,
            ArrayList::new,
            multiplyTask
        );

        assertThat(result).containsExactly(input.stream().map(i->i * 10).toArray(Integer[]::new));
    }

}
