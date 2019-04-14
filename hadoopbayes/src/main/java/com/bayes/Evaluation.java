package com.bayes;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;


/**
 * @Author: Konfuse
 * @Date: 19-4-14 下午9:32
 */
public class Evaluation {
    /*
     * 得到原本的文档分类
     * 输入:初始数据集合,格式为<<ClassName:Doc>,word1 word2...>
     * 输出:原本的文档分类，即<ClassName,Doc>
     */
    public static class OriginalDocOfClassMap extends Mapper<Text, Text, Text, Text> {
        private Text newKey = new Text();
        private Text newValue = new Text();

        @Override
        protected void map(Text key, Text value, Context context) throws IOException, InterruptedException {
            newKey.set(key.toString().split(":")[0]);
            newValue.set(key.toString().split(":")[1]);
            context.write(newKey, newValue);
        }
    }
}
