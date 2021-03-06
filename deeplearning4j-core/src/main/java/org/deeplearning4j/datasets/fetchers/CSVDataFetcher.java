package org.deeplearning4j.datasets.fetchers;

import au.com.bytecode.opencsv.CSV;
import au.com.bytecode.opencsv.CSVReadProc;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.datasets.DataSet;
import org.deeplearning4j.util.MatrixUtil;
import org.jblas.DoubleMatrix;
import org.springframework.util.StringUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CSV record based data fetcher
 *
 * @author Adam Gibson
 */
public class CSVDataFetcher extends BaseDataFetcher {

    private  CSV csv;    // new instance is immutable


    private InputStream is;
    private int labelColumn;
    private DataSet all;


    /**
     * Constructs a csv data fetcher with the specified label column
     * skipping no lines
     * @param is the input stream to read from
     * @param labelColumn the index of the column (0 based)
     */
    public CSVDataFetcher(InputStream is,int labelColumn) {
        this.is = is;
        this.labelColumn = labelColumn;
        csv = CSV.skipLines(0)
                .separator(',')  // delimiter of fields
                .quote('"')      // quote character
                .create();       // new instance is immutable

        init();
    }


    /**
     * Constructs a csv data fetcher with the specified
     * label column skipping no lines
     * @param f the file to read from
     * @param labelColumn the index of the column (0 based)
     * @throws IOException
     */
    public CSVDataFetcher(File f,int labelColumn) throws IOException {
        this(new BufferedInputStream(new FileInputStream(f)),labelColumn,0);
    }

    /**
     * Constructs a csv data fetcher with the specified number of lines to skip
     * @param is the input stream to read from
     * @param labelColumn the index of the column (0 based)
     * @param skipLines the number of lines to skip
     */
    public CSVDataFetcher(InputStream is,int labelColumn,int skipLines) {
        this.is = is;
        this.labelColumn = labelColumn;
        csv = CSV.skipLines(skipLines)
                .separator(',')  // delimiter of fields
                .noQuote()     // quote character
                .create();       // new instance is immutable

        init();
    }

    /**
     * Constructs a csv data fetcher with the specified number of lines to skip
     * @param f the file to read from
     * @param labelColumn the index of the column (0 based)
     * @param skipLines the number of lines to skip
     * @throws IOException
     */
    public CSVDataFetcher(File f,int labelColumn,int skipLines) throws IOException {
        this(new BufferedInputStream(new FileInputStream(f)),labelColumn,skipLines);
    }


    private void init() {
        final Set<Integer> labels = new HashSet<>();
        final List<Integer> rowLabels = new ArrayList<>();
        final List<DoubleMatrix> features = new ArrayList<>();
        final AtomicInteger i1 = new AtomicInteger(-1);
        csv.read(is,new CSVReadProc() {
            @Override
            public void procRow(int rowIndex, String... values) {
                if(values.length < 1)
                    return;
                if(i1.get() < 1) {
                    i1.set(values.length - 1);
                    CSVDataFetcher.this.inputColumns = values.length - 1;

                }
                else if(values.length  - 1 != i1.get())
                    return;
                Pair<DoubleMatrix,Integer> row = processRow(values);
                rowLabels.add(row.getSecond());
                labels.add(row.getSecond());
                features.add(row.getFirst());
            }
        });

        List<DataSet> l = new ArrayList<>();
        for(int i = 0; i < rowLabels.size(); i++) {
            l.add(new DataSet(features.get(i),MatrixUtil.toOutcomeVector(rowLabels.get(i),rowLabels.size())));
        }

        this.numOutcomes = rowLabels.size();
        all = DataSet.merge(l);


    }


    private Pair<DoubleMatrix,Integer> processRow(String[] data) {


        String label = data[labelColumn].replaceAll(".\".","");
        Double labelVal = Double.parseDouble(label);
        List<Double> vals = new ArrayList<>();
        for(int i = 0; i < data.length; i++)
            if(i != labelVal)
                vals.add(Double.parseDouble(data[i]));

        double[] d = new double[vals.size()];
        DoubleMatrix d1 = new DoubleMatrix(d).reshape(1,d.length);
        return new Pair<>(d1,(int) labelVal.doubleValue());

    }

    /**
     * Fetches the next dataset. You need to call this
     * to get a new dataset, otherwise {@link #next()}
     * just returns the last data set fetch
     *
     * @param numExamples the number of examples to fetch
     */
    @Override
    public void fetch(int numExamples) {
        int end = cursor + numExamples;
        if(end >= all.numExamples())
            end = all.numExamples();
        initializeCurrFromList(all.asList().subList(cursor,end));
        cursor += numExamples;
    }
}
