package com.google.android.gms.samples.vision.ocrreader;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        Bundle b = getIntent().getExtras();
        String nama = b.getString("nama");
        String kuantiti = b.getString("quantity");
        String harga = b.getString("harga");

        String[] names = nama.split("\n");
        String[] quantitys = kuantiti.split("\n");
        String[] prices = harga.split("\n");

        Pattern patternName = Pattern.compile("^[A-Za-z0-9]*[A-Za-z0-9][A-Za-z0-9 ]*$");
        Pattern patternQuantity = Pattern.compile("^[0-9]*[0-9][0-9 ]*$");
        Pattern patternPrice = Pattern.compile("^[R][p]*[0-9][0-9]*$");

        int validNames = 0;
        for (String name : names) {
            Matcher matcher = patternName.matcher(name);
            if (matcher.matches())
                validNames++;
        }

        int validQuantity = 0;
        for (String quantity : quantitys) {
            Matcher matcher = patternQuantity.matcher(quantity);
            if (matcher.matches())
                validQuantity++;
        }

        int validPrice = 0;
        for (String price : prices) {
            Matcher matcher = patternPrice.matcher(price);
            if (matcher.matches())
                validPrice++;
        }

        Log.e("RESULT", "nama: " + validNames + " - " + " quantity: "
                + validQuantity + " - " + " harga: " + validPrice);

        TextView txNama = findViewById(R.id.tx_nama);
        TextView txQuantity = findViewById(R.id.tx_quantity);
        TextView txHarga = findViewById(R.id.tx_harga);

        txNama.setText(nama);
        txQuantity.setText(kuantiti);
        txHarga.setText(harga);
    }
}
