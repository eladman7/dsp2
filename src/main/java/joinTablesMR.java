import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

import java.io.IOException;

public class joinTablesMR {
    public static class MapperClass extends Mapper<LongWritable, Text, Text, IntWritable> {

        @Override
        public void map(LongWritable lineId, Text line, Context context) throws IOException,  InterruptedException {
            String[] words = line.toString().split("\\s+");
            //Assuming work on 1 gram

            if(words.length == 3) { // Reading from 1Gram
                context.write(new Text(words[0] + "\t" + words[1] + "\t" + "1gram"), new IntWritable(Integer.valueOf(words[2])));
//                context.write(new Text("Decade:" + findDecade(words[1])), new IntWritable(Integer.valueOf(words[2])));
            }
            else if (words.length == 4) { // Reading from 2Gram
                context.write(new Text(words[0] + " " + words[1] + "\t" + words[2] + "\t" + "2gram"), new IntWritable(Integer.valueOf(words[3])));
//                 context.write(new Text("Decade:" + findDecade(words[2])), new IntWritable(Integer.valueOf(words[3])));
            }

        }


    }

    public static class ReducerClass extends Reducer<Text,IntWritable,Text,IntWritable> {
        long kVal = 0;

        @Override
        public void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException,  InterruptedException {
            kVal

            int sum = 0;
            for (IntWritable value : values) {
                sum += value.get();
            }
            context.write(key, new IntWritable(sum));
        }
    }

    public static class PartitionerClass extends Partitioner<Text, IntWritable> {
        @Override
        public int getPartition(Text key, IntWritable value, int numPartitions) {
            return key.hashCode() % numPartitions;
        }
    }

    public static void main(String[] args) throws Exception {


        Configuration conf = new Configuration();
        Job job = new Job(conf, "joinTables");
        job.setJarByClass(joinTablesMR.class);
        job.setMapperClass(MapperClass.class);
        job.setPartitionerClass(PartitionerClass.class);
//        job.setCombinerClass(ReducerClass.class);
        job.setReducerClass(ReducerClass.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(IntWritable.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(LongWritable.class);
        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);

        Path oneGram = new Path(args[0]);
        Path twoGram = new Path(args[1]);
//        Path decs = new Path(args[2]);
        Path outputPath = new Path(args[3]);
        MultipleInputs.addInputPath(job, oneGram, TextInputFormat.class, MapperClass.class);
        MultipleInputs.addInputPath(job, twoGram, TextInputFormat.class, MapperClass.class);
//        MultipleInputs.addInputPath(job, decs, TextInputFormat.class, MapperClass.class);

        FileOutputFormat.setOutputPath(job, outputPath);

        System.exit(job.waitForCompletion(true) ? 0 : 1);

//        // Defines additional single text based output 'text' for the job
//        MultipleOutputs.addNamedOutput(job, "Decs", TextOutputFormat.class,
//                Text.class, LongWritable.class);
//
//        // Defines additional sequence-file based output 'sequence' for the job
//        MultipleOutputs.addNamedOutput(job, "1gram", TextOutputFormat.class,
//                Text.class, LongWritable.class);
//
//        MultipleOutputs.addNamedOutput(job, "2gram", TextOutputFormat.class,
//                Text.class, LongWritable.class);

    }
}
