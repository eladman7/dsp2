import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

public class MapReduceToDecades {
    // TODO: 17/05/2020 add Better combiner, add stop words check.
    public static class MapperClass extends Mapper<Text, GoogleGram, Text, LongWritable> {
        static Set<String> engStopWords;
        static Set<String> hebStopWords;

        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            try (InputStream in = MapReduceToDecades.class.getClassLoader().getResourceAsStream("englishStopWords")) {
                if (in != null) {
                    engStopWords = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                            .lines()
                            .collect(Collectors.toSet());
                }
            }
            try (InputStream in = MapReduceToDecades.class.getClassLoader().getResourceAsStream("englishStopWords")) {
                if (in != null) {
                    hebStopWords = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                            .lines()
                            .collect(Collectors.toSet());
                }
            }
        }

        @Override
        public void map(Text lineId, GoogleGram gram, Context context) throws IOException, InterruptedException {
            //Assuming work on 1 gram
            String[] splitGram = gram.getGram().split("\\s+");
            if (splitGram.length == 1) {
                context.write(new Text("1gram:" + splitGram[0] + "\t" + findDecade(gram.getYear())),
                        new LongWritable((gram.getOccurrences())));
                context.write(new Text("Decade:" + findDecade(gram.getYear())),
                        new LongWritable(gram.getOccurrences()));
            } else if (splitGram.length == 2) { // 2 gram
                if (stopWord(splitGram[0]) || stopWord(splitGram[1])) return;
                context.write(new Text("2gram:" + splitGram[0] + " " + splitGram[1] + "\t" + findDecade(gram.getYear()))
                        , new LongWritable(gram.getOccurrences()));
            }
        }

        private boolean stopWord(String word) {
            return engStopWords.contains(word) || hebStopWords.contains(word);
        }

        private String findDecade(String word) {
            String dec = word.substring(0, word.length() - 1);
            return dec + "0-" + dec + "9";
        }
    }

    public static class ReducerClass extends Reducer<Text, LongWritable, Text, LongWritable> {
        private MultipleOutputs<Text, LongWritable> mo;

        public void setup(Context context) {
            mo = new MultipleOutputs<>(context);
        }

        @Override
        public void reduce(Text key, Iterable<LongWritable> values, Context context) throws IOException, InterruptedException {
            long decadeCount = 0;
            StringBuilder sKey = new StringBuilder(key.toString());
            if (sKey.toString().startsWith("Decade:")) {
                for (LongWritable count : values) {
                    decadeCount += count.get();
                }
                String newKey;
                newKey = sKey.substring("Decade:".length());
                mo.write(new Text(newKey), new LongWritable(decadeCount), "Decs");
            } else {
                int sum = 0;
                for (LongWritable value : values) {
                    sum += value.get();
                }
                if (sKey.toString().startsWith("1gram:"))
                    mo.write(new Text(sKey.substring("1gram:".length())), new LongWritable(sum), "1gram");
                else
                    mo.write(new Text(sKey.substring("2gram:".length())), new LongWritable(sum), "2gram");
            }
        }

        public void cleanup(Context context) throws InterruptedException, IOException {
            mo.close();
        }
    }

    public static class PartitionerClass extends Partitioner<Text, LongWritable> {
        @Override
        public int getPartition(Text key, LongWritable value, int numPartitions) {
            return key.hashCode() % numPartitions;
        }
    }


    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();

        Job job = new Job(conf, "gramsUnionAndDecsCalc");
        job.setJarByClass(MapReduceToDecades.class);
        job.setMapperClass(MapperClass.class);
        job.setPartitionerClass(PartitionerClass.class);
//        job.setCombinerClass(ReducerClass.class);
        job.setReducerClass(ReducerClass.class);
        // mapper output
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(LongWritable.class);
        // reducer output
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(LongWritable.class);

        // job input
        job.setInputFormatClass(GoogleGramInputFormat.class);
        // job output
        job.setOutputFormatClass(TextOutputFormat.class);

        Path oneGram = new Path(args[0]);
        Path twoGram = new Path(args[1]);
        Path outputPath = new Path(args[2]);
        MultipleInputs.addInputPath(job, oneGram, GoogleGramInputFormat.class, MapperClass.class);
        MultipleInputs.addInputPath(job, twoGram, GoogleGramInputFormat.class, MapperClass.class);

        // Defines additional single text based output 'text' for the job
        MultipleOutputs.addNamedOutput(job, "Decs", TextOutputFormat.class,
                Text.class, LongWritable.class);

        // Defines additional sequence-file based output 'sequence' for the job
        MultipleOutputs.addNamedOutput(job, "1gram", TextOutputFormat.class,
                Text.class, LongWritable.class);

        MultipleOutputs.addNamedOutput(job, "2gram", TextOutputFormat.class,
                Text.class, LongWritable.class);

        FileOutputFormat.setOutputPath(job, outputPath);

        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}