package com.aac.svs

import android.app.Activity
import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.smartreply.SmartReply
import com.google.mlkit.nl.smartreply.SmartReplyGeneratorOptions
import com.google.mlkit.nl.smartreply.SmartReplySuggestionResult
import com.google.mlkit.nl.smartreply.TextMessage
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.regex.Matcher
import java.util.regex.Pattern


class MainActivity : AppCompatActivity() {
    private lateinit var textToSpeech: TextToSpeechManager
    //init debug_textview to show debug info
    lateinit var debug_textview: TextView
    lateinit var conversation_textview: TextView
    private lateinit var add_dialog: Dialog
    lateinit var PortugueseToEnglish: Translator
    lateinit var EnglishToPortuguese: Translator
    lateinit var conversation : List<TextMessage>
    var popup_checkbox = "unchecked"
    var last_message = "Hello, I'm starting the conversation"
    val dbHelper = DatabaseHelper(this)
    val fragmentoA= FragmentA()
    var repeated_replies = 0
    var cont_replies = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        FragmentChange()
        SetupDialog()
        SetupMicrophone()
        SetupTranslate()
        SetupConversation()

        debug_textview = findViewById(R.id.debug_textview)
        conversation_textview = findViewById(R.id.conversation_textview)
        //smart_reply()
        // Text to speech
        textToSpeech = TextToSpeechManager(this)

        //define floating_speak_button
        val floatingSpeakButton = findViewById<FloatingActionButton>(R.id.floating_speak_button)
        //listener
        floatingSpeakButton.setOnClickListener {
            //get text from editText_A in fragmentA and speak it
            val editTextA = findViewById<EditText>(R.id.editText_A)
            val text = editTextA.text.toString()
            textToSpeech.speak(text)
        }
    }

    fun getImageNamesAndIds(): Pair<List<String>, List<Int>> {
        val imageNames = ArrayList<String>()
        val imageIds = ArrayList<Int>()

        val resources = resources
        val packageName = packageName

        try {
            val fields = R.drawable::class.java.fields
            for (field in fields) {
                if (field.type == Int::class.java) {
                    val imageName = field.name
                    val imageId = resources.getIdentifier(imageName, "drawable", packageName)
                    if (imageId != 0 && !imageName.contains("ic_")) {
                        imageNames.add(imageName)
                        imageIds.add(imageId)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Pair(imageNames, imageIds)
    }

    fun SetupMicrophone(){
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale algo!")
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")

        val microphoneButton = findViewById<ImageView>(R.id.floating_micro_button)

        microphoneButton.setOnClickListener {
            resultLauncher.launch(intent)
        }
    }
    private val resultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val textList = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!textList.isNullOrEmpty()) {
                val recognizedText = textList[0]
                println("recognizedText: $recognizedText")

                val translated_text = Port2Eng(recognizedText)
            }
        }
    }
    fun Port2Eng(text: String): String {
        var translatedText = ""
        PortugueseToEnglish.translate(text)
            .addOnSuccessListener { translatedText = it
            //update conversation
                //is firts suggestion is not emoji, add to conversation

                update_conversation(last_message, true)
                update_conversation(translatedText, false)
                smart_reply()
            }
            .addOnFailureListener { exception ->
                println("Falha ao traduzir texto: $exception")
            }
        return translatedText
    }

    fun Eng2Port(text: String): String {
        var translatedText = ""
        EnglishToPortuguese.translate(text)
            .addOnSuccessListener { translatedText = it
            println("translatedText _______: $translatedText")
            //create line to save to database
            val db = dbHelper.writableDatabase
                //verificar si el texto ya existe en la base de datos
                val cursor = db.rawQuery("SELECT * FROM TusDatos WHERE text = '$translatedText'", null)
                val values = ContentValues().apply {
                    put("icon", "ic_empty_button")
                    put("icon_id", 0)
                    put("image_id", 0)
                    put("text", translatedText)
                    put("mode", "text")
                    put("smart_reply", true)}

                if (cursor.count == 0) {
                    //no existe, agregar a la base de datos
                    db.insert("TusDatos", null, values)
                }else{
                    //eliminar el elemento de la base de datos y agregarlo de nuevo
                    db.delete("TusDatos", "text = '$translatedText'", null)
                    db.insert("TusDatos", null, values)
                    
                    repeated_replies += 1
                    //imprimir translatedText junto con el elemento que no se agrego
                    println("translatedText already exist in database:  $translatedText")
                }



            db.close()
            //refresh fragment
            refreshFragment()
            debug_textview.text = "$cont_replies replies generated / $repeated_replies repeated \n ___________________________ \n"
            }
            .addOnFailureListener { exception ->
                println("Falha ao traduzir texto: $exception")
            }
        //show conversation in conversation_textview
        conversation_textview.text = ""
        for (message in conversation) {
            if (message != null) {
                conversation_textview.text = conversation_textview.text.toString() + "\n" + message.messageText
            }
        }
        return translatedText

    }

    fun SetupTranslate(){
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.PORTUGUESE)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
        PortugueseToEnglish = Translation.getClient(options)
        var conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()
        PortugueseToEnglish.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                println("Modelo Port2Eng baixado com sucesso")
            }
            .addOnFailureListener { exception ->
                println("Falha ao baixar modelo Port2Eng: $exception")
            }

        //setup EnglishToPortuguese
        val options2 = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.PORTUGUESE)
            .build()
        EnglishToPortuguese = Translation.getClient(options2)
        conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()
        EnglishToPortuguese.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                println("Modelo Eng2Port baixado com sucesso")
            }
            .addOnFailureListener { exception ->
                println("Falha ao baixar modelo Eng2Port: $exception")
            }
    }



    fun SetupDialog(){

        val add_dialog= Dialog(this)
        add_dialog.setContentView(R.layout.add_popup)
        //cambiar tamaño del dialogo
        add_dialog.window?.setLayout(800, 1100)

        //Definie Spinner with images inside dialog popup
        val spinnerInDialog = add_dialog.findViewById<Spinner>(R.id.icon_spinner)
        val (imageNames, imageIds)= getImageNamesAndIds()
        val customAdapter = CustomSpinnerAdapter(this, spinnerInDialog, imageNames, imageIds)
        spinnerInDialog.adapter = customAdapter

        val floatindAddButton = findViewById<FloatingActionButton>(R.id.floating_add_button)
        floatindAddButton.setOnClickListener {
            add_dialog.show()
        }

        //do something when item is selected
        val editTextToModify = add_dialog.findViewById<EditText>(R.id.popup_textEntry)
        spinnerInDialog.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View,
                position: Int,
                id: Long){
                val item = parent.getItemAtPosition(position).toString()
                editTextToModify.setText(item)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                // write code to perform some action
            }
        }

        //add function to dialog_button
        val add_dialogButton = add_dialog.findViewById<Button>(R.id.dialog_add_button)
        add_dialogButton.setOnClickListener {
            //obtener el texto del edit text y el icono del spinner
            val editText = add_dialog.findViewById<TextView>(R.id.popup_textEntry)
            val spinner = add_dialog.findViewById<Spinner>(R.id.icon_spinner)
            val icon = spinner.selectedItem.toString()
            val icon_id = spinner.selectedItemId
            val image_id = imageIds[icon_id.toInt()]
            val text = editText.text.toString()
            var mode = "icon"

            if (popup_checkbox == "checked") {
                mode = "text"
            }
            //add to database
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("icon", icon)
                put("icon_id", icon_id)
                put("image_id", image_id)
                put("text", text)
                put("mode", mode)
                put("smart_reply", false)
            }
            val newRowId = db?.insert("TusDatos", null, values)
            //close dialog
            add_dialog.dismiss()
            db.close()

            //refresh fragment
            refreshFragment()
        }

        val checkBoxPopup= add_dialog.findViewById<CheckBox>(R.id.checkBox_popup)
        //if checkbox is checked, hide spinner
        checkBoxPopup.setOnClickListener {
            if (checkBoxPopup.isChecked) {
                spinnerInDialog.isEnabled = false
                spinnerInDialog.background.alpha = 128
                popup_checkbox = "checked"
            }else{
                spinnerInDialog.isEnabled = true
                spinnerInDialog.background.alpha = 255
                popup_checkbox = "unchecked"
            }
        }
    }

    fun add_to_database(icon: String, icon_id: Int, image_id: Int, text: String, mode: String, smart_reply: Boolean){
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("icon", icon)
            put("icon_id", icon_id)
            put("image_id", image_id)
            put("text", text)
            put("mode", mode)
            put("smart_reply", smart_reply)
        }
        val newRowId = db?.insert("TusDatos", null, values)
        db.close()
    }

    fun refreshFragment(){
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, FragmentA()) // Reemplaza "fragmentContainer" con el ID de tu contenedor
            .commit()
    }
    fun FragmentChange(){
        //iniciar con fragmentoA
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, FragmentA()) // Reemplaza "fragmentContainer" con el ID de tu contenedor
            .commit()

        val buttonA= findViewById<Button>(R.id.button_fragment_a)
        buttonA.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, FragmentA()) // Reemplaza "fragmentContainer" con el ID de tu contenedor
                .commit()
        }
        val buttonB= findViewById<Button>(R.id.button_fragment_b)
        buttonB.setOnClickListener {

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, FragmentB()) // Reemplaza "fragmentContainer" con el ID de tu contenedor
                .commit()
        }
        val buttonC= findViewById<Button>(R.id.button_fragment_c)
        buttonC.setOnClickListener {
            textToSpeech.speak("Meu nome é Santiago")
        }
    }

    fun update_conversation(message: String, user: Boolean = true){
        //add message to conversation
        println("SVS4 " + message)
        if (user==false){
            //mesagge from remote user
            conversation = conversation + TextMessage.createForRemoteUser(message, System.currentTimeMillis(), "remote_user_id")
        }
        else{
            conversation = conversation + TextMessage.createForLocalUser(message, System.currentTimeMillis())
        }
        //mostrar conversation en consola
        for (message in conversation) {
            if (message != null) {
                println("message: ${message}")
            }
        }
    }

    fun SetupConversation() {
        conversation = listOf(
            TextMessage.createForLocalUser("Hello", System.currentTimeMillis())
        )

    }


    fun smart_reply(){
        repeated_replies = 0
        cont_replies = 0
        val smartReplyGenerator = SmartReply.getClient(SmartReplyGeneratorOptions.Builder().build())
        smartReplyGenerator.suggestReplies(conversation)
            .addOnSuccessListener { result ->
                if (result.status == SmartReplySuggestionResult.STATUS_NOT_SUPPORTED_LANGUAGE) {
                    // The conversation's language isn't supported, so the
                    // the result doesn't contain any suggestions.
                    println("The conversation's language isn't supported, so the the result doesn't contain any suggestions.")
                } else if (result.status == SmartReplySuggestionResult.STATUS_SUCCESS) {
                    // Task completed successfully
                    // ...
                    println("Task completed successfully")
                    val suggestions = result.suggestions
                    //get len of suggestions and print in debug_textview


                    for (suggestion in suggestions) {
                        if (isEmoji(suggestion.text)){
                            println("isEmoji")
                        }
                        else{
                            cont_replies += 1
                            last_message = suggestion.text
                            if (last_message == ""){
                                last_message = "ok"
                                println("last_message CAMBIADO: $last_message")
                            }
                            val replyText = suggestion.text
                            println("replyText: $replyText")
                            Eng2Port(replyText)
                        }
                    }

                }
            }
            .addOnFailureListener { exception ->
                // Task failed with an exception
                // ...
                println("Task failed with an exception: $exception")
            }

    }
    fun isEmoji(text: String?): Boolean {
        // Expresión regular para detectar emojis Unicode
        val emojiPattern = "[\\p{So}]"
        val pattern: Pattern = Pattern.compile(emojiPattern)
        val matcher: Matcher = pattern.matcher(text)

        // Si se encuentra al menos un emoji en el texto, devuelve true; de lo contrario, devuelve false
        return matcher.find()
    }
    override fun onDestroy() {
        super.onDestroy()
    }

}

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "TuBaseDeDatos.db"
        private const val DATABASE_VERSION = 1
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Define la estructura de tus tablas y crea las tablas
        val createTableSQL = "CREATE TABLE TusDatos (_id INTEGER PRIMARY KEY, icon TEXT, icon_id INTEGER, image_id INTEGER, text TEXT, mode TEXT, smart_reply BOOLEAN);"
        db.execSQL(createTableSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Puedes manejar las actualizaciones de la base de datos aquí
    }
}


class CustomSpinnerAdapter(
    private val context: MainActivity,
    private val spinner: Spinner,
    private val imageNames: List<String>,
    private val imageIds: List<Int>
) : ArrayAdapter<String>(context, R.layout.item_spinner, imageNames) {

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getCustomView(position, parent)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getCustomView(position, parent)
    }

    private fun getCustomView(position: Int, parent: ViewGroup): View {
        val inflater = context.layoutInflater
        val row = inflater.inflate(R.layout.item_spinner, parent, false)

        val imageView = row.findViewById<ImageView>(R.id.spinner_image_view)
        val textView = row.findViewById<TextView>(R.id.spinner_text_view)

        val drawable = ContextCompat.getDrawable(context, imageIds[position])
        imageView.setImageDrawable(drawable)

        textView.text = imageNames[position]

        return row
    }
}
