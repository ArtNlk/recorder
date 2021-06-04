package com.example.recorder;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class passwordActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.content_password);

    }

    public void onClick(View view)
    {
        switch (view.getId())
        {
            case R.id.submitButton:
                EditText passwordField = findViewById(R.id.textPasswordField);
                TextView testTextView = findViewById(R.id.textView);

                if(passwordField.getText().toString().equals("4rt3m2oo2"))
                {
                    setResult(Activity.RESULT_OK);
                    finish();
                }
                else
                {
                    testTextView.setText("Wrong password!");
                    testTextView.setTextColor(Color.RED);
                }
                break;
        }
    }

    @Override
    public void onBackPressed() {
        return;
    }
}