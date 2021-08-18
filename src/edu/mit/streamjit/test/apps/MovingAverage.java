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

public final class MovingAverage {
    private MovingAverage() {}

    public static void main(String[] args) throws InterruptedException {
		StreamCompiler sc = new DebugStreamCompiler();
		Benchmarker.runBenchmark(new MovingAverageBenchmark(), sc).get(0).print(System.out);
	}

    static class VT {
        public float val;
        public int ts;
    
        public VT(float v, int t){
            val = v;
            ts = t;
        }
    
        @Override
        public String toString() {
            return "{"+val+","+ts+"}";
        }
    }

    @ServiceProvider(Benchmark.class)
	public static final class MovingAverageBenchmark extends SuppliedBenchmark { 
        public static ArrayList<VT> getSource(){
            ArrayList<VT> source =  new ArrayList<VT>();
            String line = "";
            try   
            {  
                BufferedReader br = new BufferedReader(new FileReader("C:/Users/yudis/Documents/university/Summer2021/Code/streamjit/src/edu/mit/streamjit/test/apps/10_random.csv")); 
                br.readLine(); 
                while ((line = br.readLine()) != null)   
                {  
                String[] row = line.split(",");
                source.add(new VT(Float.parseFloat(row[0]),Integer.parseInt(row[1])));
                    } 
                br.close(); 
            }   
            catch (IOException e)   
            {  
                e.printStackTrace();
            }
            return source;
        }

        private static final ArrayList<VT> mydata = getSource();

        public MovingAverageBenchmark() {        
        super("MovingAverage", MovingAverageTopLevel.class, new Dataset("Datasource", (Input)Datasets.nCopies(1, (Input)Input.fromIterable(mydata))
			));
		}
    }

    private static final class MovingAverageTopLevel extends Pipeline<VT, Void>{
        public MovingAverageTopLevel() {
            super(new ComputeMovingAverage(), new StreamPrinter<Float>());
        }
    }

    private static final class ComputeMovingAverage extends Filter<VT, Float>{
        //private final int num_days;

        public ComputeMovingAverage(){
            super(1,1,2);
            //this.num_days = num_days;
        }

        @Override
        public void work() {
            float sum = 0;
            for (int i = 0; i < 2; i++){
                sum += peek(i).val;
            }
            push(sum/2);
            pop();
        }
    }
}

    

