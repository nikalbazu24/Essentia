package com.hatkid.mkxpz;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import com.hatkid.mkxpz.ui.ControllerActivity;
import com.hatkid.mkxpz.ui.Ui;

public class AiTranslationActivity extends ControllerActivity {
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);LinearLayout list=new LinearLayout(this);list.setOrientation(1);int p=Ui.dp(this,20);list.setPadding(p,p,p,p);list.setBackgroundColor(Ui.BG);
        ScrollView scroll=new ScrollView(this);scroll.addView(list);setContentView(scroll);
        Button back=Ui.button(this,"‹ Volver",false);back.setOnClickListener(v->finish());list.addView(back);
        list.addView(Ui.text(this,"Traducción IA opcional · Gemini",24,Ui.TEXT,true));
        list.addView(Ui.text(this,"Está desactivada por defecto. No necesitas una API para usar Essentia ni su traducción habitual. Esta opción requiere tu propia clave de Gemini API; una suscripción a la app Gemini no garantiza crédito de API. Puede generar cobros y enviar a Google los textos del juego. Revisa tu cuota y presupuesto en Google antes de activarla.",16,Ui.TEXT_DIM,false));
        list.addView(Ui.text(this,"Límite local: 20 solicitudes al día, hasta 6000 caracteres por lote. No es un límite de gasto garantizado. Las traducciones ya guardadas se reutilizan. Si la API falla, esta opción se apaga.",15,Ui.TEXT_DIM,false));
        Button configure=Ui.button(this,"Configurar proveedor opcional",false);list.addView(configure);
        LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setBackground(Ui.rounded(this,Ui.SURFACE,16));form.setPadding(p,p,p,p);list.addView(form);
        form.setVisibility(AiTranslation.enabled(this)?android.view.View.VISIBLE:android.view.View.GONE);
        configure.setOnClickListener(v->form.setVisibility(form.getVisibility()==android.view.View.VISIBLE?android.view.View.GONE:android.view.View.VISIBLE));
        EditText model=com.hatkid.mkxpz.ui.Ui.input(this);model.setTextColor(Ui.TEXT);model.setHint("Nombre del modelo habilitado en tu cuenta API");model.setText(AiTranslation.prefs(this).getString("model",""));form.addView(model);
        EditText key=com.hatkid.mkxpz.ui.Ui.input(this);key.setTextColor(Ui.TEXT);key.setHint("Clave nueva (vacío conserva la guardada)");key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);form.addView(key);
        CheckBox active=new CheckBox(this);active.setText("Activar traducción mediante mi propia API");active.setTextColor(Ui.TEXT);active.setChecked(AiTranslation.enabled(this));form.addView(active);
        String error=AiTranslation.prefs(this).getString("last_error","");if(!error.isEmpty())form.addView(Ui.text(this,error,15,Ui.GOLD,false));
        Button save=Ui.button(this,"Guardar mi elección",false);form.addView(save);
        save.setOnClickListener(v->{
            Runnable apply=()->{
                try{
                    String value=model.getText().toString().trim();
                    if(active.isChecked()&&!value.matches("[a-zA-Z0-9._-]{1,100}"))throw new Exception("Indica un modelo válido de Gemini API");
                    if(active.isChecked()&&Games.isGameRunning(this))throw new Exception("Cierra los juegos antes de activar o cambiar la API");
                    if(!key.getText().toString().trim().isEmpty())AiTranslation.saveKey(this,key.getText().toString().trim());
                    if(active.isChecked()&&!AiTranslation.prefs(this).contains("key"))throw new Exception("Falta tu clave de API");
                    AiTranslation.prefs(this).edit().putString("model",value).remove("last_error").commit();
                    AiTranslation.setEnabled(this,active.isChecked());
                    key.setText("");Toast.makeText(this,"Elección guardada. Abre de nuevo el juego para aplicarla.",Toast.LENGTH_LONG).show();
                }catch(Exception e){new AlertDialog.Builder(this).setMessage(e.getMessage()).setPositiveButton("Aceptar",null).show();}
            };
            if(active.isChecked())new AlertDialog.Builder(this).setTitle("Activar mi API de Gemini")
                .setMessage("Los textos pendientes se enviarán a Google al jugar con traducción. Tu cuenta API puede cobrar por ello. ¿Quieres activar esta opción?")
                .setNegativeButton("Cancelar",null).setPositiveButton("Sí, activar mi API",(d,w)->apply.run()).show();
            else apply.run();
        });
        Button remove=Ui.button(this,"Borrar mi clave y desactivar",false);form.addView(remove);
        remove.setOnClickListener(v->{
            try{AiTranslation.setEnabled(this,false);AiTranslation.prefs(this).edit().clear().commit();active.setChecked(false);key.setText("");Toast.makeText(this,"Clave retirada y API desactivada",Toast.LENGTH_SHORT).show();}
            catch(Exception e){new AlertDialog.Builder(this).setMessage(e.getMessage()).setPositiveButton("Aceptar",null).show();}
        });
    }
}
