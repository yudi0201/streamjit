package edu.mit.streamjit.test.apps;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.UnmodifiableIterator;
import com.jeffreybosboom.serviceproviderprocessor.ServiceProvider;
import edu.mit.streamjit.api.DuplicateSplitter;
import edu.mit.streamjit.api.Filter;
import edu.mit.streamjit.api.Identity;
import edu.mit.streamjit.api.Input;
import edu.mit.streamjit.api.OneToOneElement;
import edu.mit.streamjit.api.Pipeline;
import edu.mit.streamjit.api.RoundrobinJoiner;
import edu.mit.streamjit.api.RoundrobinSplitter;
import edu.mit.streamjit.api.Splitjoin;
import edu.mit.streamjit.api.StatefulFilter;
import edu.mit.streamjit.api.StreamCompiler;
import edu.mit.streamjit.api.StreamPrinter;
import edu.mit.streamjit.api.WeightedRoundrobinJoiner;
import edu.mit.streamjit.api.WeightedRoundrobinSplitter;
import edu.mit.streamjit.impl.interp.DebugStreamCompiler;
import edu.mit.streamjit.test.Benchmark;
import edu.mit.streamjit.test.Benchmarker;
import edu.mit.streamjit.test.Datasets;
import edu.mit.streamjit.test.SuppliedBenchmark;
import java.nio.ByteOrder;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.BufferedReader;  
import java.io.FileReader;  
import java.io.IOException; 

public class AlgoTradingStreamjit {
    private AlgoTradingStreamjit() {}

    public static void main(String[] args) throws InterruptedException {
		StreamCompiler sc = new DebugStreamCompiler();
		Benchmarker.runBenchmark(new AlgoTradingBenchmark(), sc).get(0).print(System.out);
	}

    static class Stock_data{
        public long date;
        public double open;
        public double high;
        public double low;
        public double close;
        public long volume;
        public String name;

        public Stock_data(long date, double open, double high, double low, double close, long volume, String name){
            this.date = date;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
            this.name = name;
        }
    }

    static class Intermediate_result{
        //public String name;
        public long result_date;
        public Double moving_average;

        public Intermediate_result(long result_date, Double moving_average){
            //this.name = name;
            this.result_date = result_date;
            this.moving_average = moving_average;
        }
    }

    static class Final_result{
        //public String name;
        public long result_date;
        public Boolean buy;

        public Final_result(long result_date, Boolean buy){
            //this.name = name;
            this.result_date = result_date;
            this.buy = buy;
        }
    }

    @ServiceProvider(Benchmark.class)
	public static final class AlgoTradingBenchmark extends SuppliedBenchmark { 
        public static ArrayList<Stock_data> getSource(){
            ArrayList<Stock_data> source =  new ArrayList<Stock_data>();
            String line = "";
            try   
            {  
                BufferedReader br = new BufferedReader(new FileReader("/home/yudi/Code")); 
                br.readLine(); 
                while ((line = br.readLine()) != null)   
                {  
                String[] row = line.split(",");
                source.add(new Stock_data(Long.parseLong(row[0]),Double.parseDouble(row[1]),Double.parseDouble(row[2]),Double.parseDouble(row[3]),Double.parseDouble(row[4]),Long.parseLong(row[5]), row[6]));
                    } 
                br.close(); 
            }   
            catch (IOException e)   
            {  
                e.printStackTrace();
            }
            return source;
        }

        private static final ArrayList<Stock_data> mydata = getSource();

        public AlgoTradingBenchmark() {        
        super("AlgoTrading", AlgoTradingTopLevel.class, new Dataset("Datasource", (Input)Datasets.nCopies(1, (Input)Input.fromIterable(mydata))
			));
		}
    }

    private static final class AlgoTradingTopLevel extends Pipeline<Stock_data, Final_result>{
        private static final int shorter = 20;
        private static final int longer = 50;

        public AlgoTradingTopLevel() {
			// The splitjoin is BPFCore in the StreamIt source.
			super(new Splitjoin<>(
					new DuplicateSplitter<Stock_data>(),
					new RoundrobinJoiner<Intermediate_result>(),
					new MovingAverageShort(shorter,longer),
					new MovingAverageLong(longer)),
				new Compare());
                //new StreamPrinter<String>());
		}
    }

    private static final class MovingAverageLong extends Filter<Stock_data, Intermediate_result>{
        private final int num_days;

        private MovingAverageLong(int num_days){
            super(1,1,num_days);
            this.num_days = num_days;
        }

        @Override
        public void work() {
            double sum = 0;
            for (int i = 0; i < num_days-1; i++){
                sum += peek(i).close;
            }
            Stock_data last_datapoint = peek(num_days-1);
            sum += last_datapoint.close;
            long date = last_datapoint.date;
            push(new Intermediate_result(date, sum/num_days));
            pop();
        }
    }

    private static final class MovingAverageShort extends Filter<Stock_data, Intermediate_result>{
        private final int shorter;
        private final int longer;

        private MovingAverageShort(int shorter, int longer){
            super(1,1,longer);
            this.shorter = shorter;
            this.longer = longer;
        }

        @Override
        public void work() {
            double sum = 0;
            for (int i = 0; i < longer-1; i++){
                double item = peek(i).close;
                if (i >= longer-shorter) {
                sum += item;
                }
            }
            Stock_data last_datapoint = peek(longer-1);
            sum += last_datapoint.close;
            long date = last_datapoint.date;
            push(new Intermediate_result(date, sum/shorter));
            pop();
        }
    }

    private static final class Compare extends Filter<Intermediate_result, Final_result>{
        private Compare(){
            super(2,1,0);
        }

        @Override
		public void work() {
			Intermediate_result a = pop(), b = pop();
            push(new Final_result(a.result_date, a.moving_average > b.moving_average));
            //Final_result result = new Final_result(a.result_date, a.moving_average > b.moving_average);
            //push(Boolean.toString(result.buy));
        }
    }

    private static final class PrintAverage extends Filter<Intermediate_result, String>{
        private PrintAverage(){
            super(2,1,0);
        }

        @Override
        public void work() {
            Intermediate_result a = pop(), b = pop();
            push("date: " + Long.toString(a.result_date) + " short_average: " + Double.toString(a.moving_average)
            + " long average: " + Double.toString(b.moving_average));
        }
    }
}
