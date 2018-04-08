/*
 * Copyright (c) 2014, Oracle America, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of Oracle nor the names of its contributors may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.yogurtpowered;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

@State(Scope.Benchmark)
public class UppercaseBenchmark {

    private static Cache<String, String> originalGuavaCache = CacheBuilder.newBuilder().maximumSize(1000000L)
            .expireAfterAccess(1, TimeUnit.HOURS).build();

    private static LoadingCache<String, String> updatedGuavaCache = CacheBuilder.newBuilder().maximumSize(1000000L)
            .expireAfterAccess(1, TimeUnit.HOURS).concurrencyLevel(50).build(new CacheLoader<String, String>() {
                @Override
                public String load(String value) throws Exception {
                    return StringUtils.upperCase(value);
                }
            });

    private static final WeakHashMap<String, WeakReference<String>> s_manualCache = new WeakHashMap<String, WeakReference<String>>(
            100000);

    private static final LRUCache<String, String> lruCache = new LRUCache<>(1_000_000);

    private static final Map<String, String> treeMap = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);

    private String originalString = "MixedCaseString";

    @Benchmark
    public void baselineNoop() {
        // do nothing, this is a baseline
    }

    @Benchmark
    public String simpleToUpperCase() {
        return originalString.toUpperCase();
    }

    @Benchmark
    public String apacheUpperCase() {
        return StringUtils.upperCase(originalString);
    }

    @Benchmark
    public String originalGuavaCachedUpperCase() {
        if (originalString != null) {
            String upperStr = originalGuavaCache.getIfPresent(originalString);
            if (upperStr == null) {
                upperStr = StringUtils.upperCase(originalString).intern();
                originalGuavaCache.put(originalString, upperStr);
            }
            return upperStr;
        } else {
            return null;
        }
    }

    @Benchmark
    public String updatedGuavaCachedUpperCase() {
        try {
            return updatedGuavaCache.get(originalString);
        } catch (ExecutionException e) {
            return null;
        }
    }

    @Benchmark
    public String manualWeakReferenceMap() {
        final WeakReference<String> cached = s_manualCache.get(originalString);
        if (cached != null) {
            final String value = cached.get();
            if (value != null)
                return value;
        }
        String upper = StringUtils.upperCase(originalString);
        s_manualCache.put(originalString, new WeakReference<String>(upper));
        return upper;
    }

    @Benchmark
    public String lruCache() {
        final String cached = lruCache.get(originalString);
        if (cached != null) {
            return cached;
        }

        final String upper = StringUtils.upperCase(originalString);
        lruCache.put(originalString, upper);
        return upper;
    }

    @Benchmark
    public String treeMap() {
        final String cached = treeMap.get(originalString);
        if (cached != null) {
            return cached;
        }

        final String upper = StringUtils.upperCase(originalString);
        treeMap.put(originalString, upper);
        return upper;
    }

    /**
     * java -jar target/benchmarks.jar UppercaseBenchmark -wi 5 -i 5 -t 4 -f 1
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(UppercaseBenchmark.class.getSimpleName()).warmupIterations(5)
                .measurementIterations(5).threads(4).forks(1).build();

        new Runner(opt).run();
    }

}
