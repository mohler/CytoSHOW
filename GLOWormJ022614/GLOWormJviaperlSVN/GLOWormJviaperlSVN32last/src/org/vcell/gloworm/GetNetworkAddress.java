package org.vcell.gloworm;

import ij.IJ;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

public class GetNetworkAddress{

   public static void main(String[] args){
   }

   public static String GetAddress(String addressType){
       String address = "FAILSONTRY";
       InetAddress lanIp = null;
        try {

            String ipAddress = null;
            Enumeration<NetworkInterface> net = null;
            net = NetworkInterface.getNetworkInterfaces();
            while(net.hasMoreElements()){
                NetworkInterface element = net.nextElement();
                Enumeration<InetAddress> addresses = element.getInetAddresses();
                while (addresses.hasMoreElements() && lanIp == null){
                    InetAddress ip = addresses.nextElement();
                    //IJ.log(ip.toString());
                    if (ip instanceof Inet4Address){

                        if (!ip.toString().contains("127.0.0.1")){
//                      if (ip.isSiteLocalAddress()){

                            ipAddress = ip.getHostAddress();
                            //IJ.log(ipAddress.toString());
                            lanIp = InetAddress.getByName(ipAddress);
                            //IJ.log(lanIp.toString());
                        }

                    }

                }
            }

            if(lanIp == null) return "LANIPNULL";

            if(addressType.equals("ip")){

                address = lanIp.toString().replaceAll("^/+", "");

            }else if(addressType.equals("mac")){

                address = getMacAddress(lanIp);
                //IJ.log(""+ipAddress+" "+lanIp+" "+address);
                if (address=="NOMACADDRESS") {
                    net = NetworkInterface.getNetworkInterfaces();
                    while(net.hasMoreElements() && address=="NOMACADDRESS"){
                        NetworkInterface element = net.nextElement();
                        Enumeration<InetAddress> addresses = element.getInetAddresses();
                        while (addresses.hasMoreElements() && address=="NOMACADDRESS"){
                            InetAddress ip = addresses.nextElement();
                            if (ip instanceof Inet4Address){

                                if (ip.isSiteLocalAddress()){

                                    ipAddress = ip.getHostAddress();
                                    lanIp = InetAddress.getByName(ipAddress);
                                    address = getMacAddress(lanIp);
                                    //IJ.log(""+ipAddress+" "+lanIp+" "+address);
                                }

                            }

                        }
                    }
                }

            }else{

                throw new Exception("Specify \"ip\" or \"mac\"");

            }

        } catch (UnknownHostException e) {

            e.printStackTrace();

        } catch (SocketException e){

            e.printStackTrace();

        } catch (Exception e){

            e.printStackTrace();

        }

       return address;

   }

   private static String getMacAddress(InetAddress ip){
       String address = "NOMACADDRESS";
        try {

            NetworkInterface network = NetworkInterface.getByInetAddress(ip);
            byte[] mac = network.getHardwareAddress();

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mac.length; i++) {
                sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));        
            }
            address = sb.toString();

        } catch (SocketException e) {

            e.printStackTrace();

        } catch (Exception e){

            e.printStackTrace();

        }

       return address;
   }

}