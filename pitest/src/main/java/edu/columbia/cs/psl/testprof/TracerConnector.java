package edu.columbia.cs.psl.testprof;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;

import org.pitest.mutationtest.engine.MutationDetails;

public class TracerConnector {
    public static ArrayList<MutationDetails> allMutations = new ArrayList<MutationDetails>();
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {

            @Override
            public void run() {
                if (allMutations.size() > 0) {
                    File f = new File(System.getProperty("mutantIdFile","mutantIDs"));
                    PrintWriter pw;
                    try {
                        pw = new PrintWriter(f);
                        for (int i = 0; i < allMutations.size(); i++) {
                            pw.print((i + 1));
                            pw.print(',');
                            pw.print(allMutations.get(i).getClassName());
                            pw.print(',');
                            pw.print(allMutations.get(i).getMethod());
                            pw.print(',');
                            pw.print(allMutations.get(i).getLineNumber());
                            pw.print(',');
                            pw.print(allMutations.get(i).getFirstIndex());
                            pw.print(',');
                            pw.print(allMutations.get(i).getMutator());
                            pw.println();
                        }
                        pw.close();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }

                }
            }
        }));
    }
}
