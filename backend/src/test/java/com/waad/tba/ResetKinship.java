package com.waad.tba;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class ResetKinship {
    public static void main(String[] args) {
        try {
            Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/tba_waad_system", "postgres", "root");
            Statement stmt = conn.createStatement();
            
            int updated = stmt.executeUpdate("UPDATE members SET kinship_verified = false WHERE relationship IS NOT NULL");
            System.out.println("Reset kinship_verified for " + updated + " members.");

            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
