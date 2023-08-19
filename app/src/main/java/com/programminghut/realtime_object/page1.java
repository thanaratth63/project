package com.programminghut.realtime_object;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;

public class page1 extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_page1);

        // เมื่อคลิกที่หน้า page1 ให้เปิดหน้า MainActivity
        findViewById(R.id.imageButton).setOnClickListener(view -> {
            Intent intent = new Intent(page1.this, MainActivity.class);
            startActivity(intent);
        });
    }
}
