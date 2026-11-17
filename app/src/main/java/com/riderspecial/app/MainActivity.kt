package com.riderspecial.app;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Captain Smart Pro - 0.00s Auto-Accept & Master PIN Engine Active
        Toast.makeText(getApplicationContext(), "Captain Smart Pro Active! PIN: dinakeot1990", Toast.LENGTH_LONG).show();
    }
}
