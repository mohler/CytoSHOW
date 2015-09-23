package client;

import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.math.BigDecimal;

import compute.Compute;
import engine.Pi;

public class ComputePi {
    public static void main(String args[]) {
//        if (System.getSecurityManager() == null) {
//            System.setSecurityManager(new SecurityManager());
//        }
        try {
            String name = args[0]+"/Compute";
            Compute comp = (Compute) Naming.lookup(name);
            int numdig =Integer.parseInt(args[1]);
            BigDecimal pi = comp.doPi(numdig);
            System.out.println(pi);
        } catch (Exception e) {
            System.err.println("ComputePi exception:");
            e.printStackTrace();
        }
    }    
}

