package com.bayes;

import java.util.Arrays;

/**
 * @Author: Konfuse
 * @Date: 19-4-12 下午9:42
 */
public class Test {
    public static void main(String[] args) {
        String[] paths = {
                "hdfs://localhost:9000/user/hadoop/bayes/training",
                "hdfs://localhost:9000/user/hadoop/bayes/ClassWordsCounts",
                "hdfs://localhost:9000/user/hadoop/bayes/ClassTotalWords",
                "hdfs://localhost:9000/user/hadoop/bayes/DiffTotalWords",
                "hdfs://localhost:9000/user/hadoop/bayes/test",
                "hdfs://localhost:9000/user/hadoop/bayes/DocToClass"
        };
        System.out.println(paths[3]);
    }
}
