package com.stomas.tocapp;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    // Variables Firebase
    private EditText txtNombre, txtEdad, txtDuracion, txtDetonante;
    private ListView lista;
    private Spinner spActividad;
    private FirebaseFirestore db;
    String[] TiposDeActividad = {"Limpieza", "Verificación", "Organización", "Pensamientos", "Otros"};

    // Variables MQTT
    private static final String mqttHost = "tcp://springeater564.cloud.shiftr.io:1883";
    private static final String IdUsuario = "AppMqtt";
    private static final String Topico = "Mensaje";
    private static final String User = "springeater564";
    private static final String Pass = "FQllKHvgSmOa1XFW";
    private MqttClient mqttClient;

    // Lista global para mantener los datos cargados
    private List<String> listaDeActividades = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar Firebase
        db = FirebaseFirestore.getInstance();

        // Vincular elementos de la interfaz
        txtNombre = findViewById(R.id.txtNombre);
        txtEdad = findViewById(R.id.txtEdad);
        txtDuracion = findViewById(R.id.txtDuracion);
        txtDetonante = findViewById(R.id.txtDetonante);
        spActividad = findViewById(R.id.spActividad);
        lista = findViewById(R.id.lista);

        // Configurar Spinner
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, TiposDeActividad);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spActividad.setAdapter(adapter);

        // Configurar MQTT
        try {
            mqttClient = new MqttClient(mqttHost, IdUsuario, null);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(User);
            options.setPassword(Pass.toCharArray());
            mqttClient.connect(options);
            Toast.makeText(this, "Conectado al servidor MQTT", Toast.LENGTH_SHORT).show();

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    Log.d("MQTT", "Conexión perdida");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    Log.d("MQTT", "Mensaje recibido: " + new String(message.getPayload()));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    Log.d("MQTT", "Entrega completa");
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }

        // Cargar lista inicial de Firestore
        CargarListaFirestore();
    }

    public void enviarDatosFirestore(View view) {
        String nombre = txtNombre.getText().toString();
        String edad = txtEdad.getText().toString();
        String duracion = txtDuracion.getText().toString();
        String detonante = txtDetonante.getText().toString();
        String tipoDeActividad = spActividad.getSelectedItem().toString();

        Map<String, Object> actividades = new HashMap<>();
        actividades.put("nombre", nombre);
        actividades.put("edad", edad);
        actividades.put("duracion", duracion);
        actividades.put("detonante", detonante);
        actividades.put("tipodeActividad", tipoDeActividad);

        db.collection("actividades")
                .document(nombre)
                .set(actividades)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Datos enviados a Firestore correctamente", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al enviar datos a Firestore: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });

        // Publicar mensaje en MQTT
        String mensaje = "Nombre: " + nombre + ", Edad: " + edad + ", Actividad: " + tipoDeActividad;
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.publish(Topico, mensaje.getBytes(), 0, false);
                Toast.makeText(this, "Mensaje enviado a MQTT", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Error: Conexión MQTT no activa", Toast.LENGTH_SHORT).show();
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void CargarLista(View view) {
        CargarListaFirestore();
    }

    public void CargarListaFirestore() {
        // Limpiar la lista global antes de cargar nuevos datos
        listaDeActividades.clear();

        db.collection("actividades")
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                String linea = "//" + document.getString("nombre") + "//" +
                                        document.getString("edad") + "//" +
                                        document.getString("duracion") + "//" +
                                        document.getString("detonante");
                                listaDeActividades.add(linea);
                            }
                            // Actualizar el adaptador del ListView con la lista global
                            ArrayAdapter<String> adaptador = new ArrayAdapter<>(
                                    MainActivity.this,
                                    android.R.layout.simple_list_item_1,
                                    listaDeActividades
                            );
                            lista.setAdapter(adaptador);
                        } else {
                            Log.e("Tag", "Error al obtener datos de Firestore", task.getException());
                        }
                    }
                });
    }
}
