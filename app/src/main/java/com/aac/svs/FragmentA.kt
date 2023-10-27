package com.aac.svs

import android.content.res.Configuration
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import com.google.mlkit.nl.smartreply.SmartReply
import com.google.mlkit.nl.smartreply.SmartReplySuggestionResult
import com.google.mlkit.nl.smartreply.TextMessage
import java.lang.Exception
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.aac.svs.TextToSpeechManager


class FragmentA : Fragment() {
    private lateinit var textToSpeech: TextToSpeechManager
    //lateinit var displayMetrics: android.util.DisplayMetrics
    lateinit var gridLayout: GridLayout
    var numColumns = 3
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        textToSpeech = TextToSpeechManager(requireContext())

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_a, container, false)

        gridLayout = view.findViewById(R.id.gridLayout)

        // Verificar la orientación de la pantalla
        val orientation = resources.configuration.orientation
        numColumns = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 5 else 3
        gridLayout.columnCount = numColumns
        gridLayout.alignmentMode = GridLayout.ALIGN_BOUNDS


        UpdateGridLayout()


        //smart_reply()

        return view
    }

    fun smart_reply(){
        val conversation = listOf(
            TextMessage.createForLocalUser("Hello", System.currentTimeMillis()),
            TextMessage.createForRemoteUser("Hi", System.currentTimeMillis(), "12345"),
            TextMessage.createForLocalUser("would you like to eat something?", System.currentTimeMillis()),
            TextMessage.createForRemoteUser("yeah! what do you have?", System.currentTimeMillis(), "12345"),
            TextMessage.createForLocalUser("I have pizza and pasta", System.currentTimeMillis()),
            TextMessage.createForRemoteUser("What do you prefer?", System.currentTimeMillis(), "12345")


        )
        val smartReply = SmartReply.getClient()
        smartReply.suggestReplies(conversation)
            .addOnSuccessListener { result ->
                if (result.status == SmartReplySuggestionResult.STATUS_NOT_SUPPORTED_LANGUAGE) {
                    // The conversation's language isn't supported, so
                    // the result doesn't contain any suggestions.
                } else if (result.status == SmartReplySuggestionResult.STATUS_SUCCESS) {
                    // Task completed successfully
                    // ...
                    for (suggestion in result.suggestions) {
                        val replyText = suggestion.text
                        println(replyText)
                    }
                }
            }
            .addOnFailureListener { e ->
                // Task failed with an exception
                // ...
            }
    }

    fun UpdateGridLayout() {
        //empty gridLayout
        gridLayout.removeAllViews()

        //get screen width
        var displayMetrics: android.util.DisplayMetrics = resources.displayMetrics
        val dpWidth = displayMetrics.widthPixels

        val dbHelper = DatabaseHelper(requireContext())
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM TusDatos", null)
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow("_id"))
                val icon = cursor.getString(cursor.getColumnIndexOrThrow("icon"))
                val icon_id = cursor.getInt(cursor.getColumnIndexOrThrow("icon_id"))
                val image_id = cursor.getInt(cursor.getColumnIndexOrThrow("image_id"))
                val text = cursor.getString(cursor.getColumnIndexOrThrow("text"))
                val mode = cursor.getString(cursor.getColumnIndexOrThrow("mode"))

                println("id: $id, icon: $icon, icon_id: $icon_id, image_id: $image_id, text: $text, mode: $mode")
                val imageView = ImageView(requireContext())
                val textView = TextView(requireContext())
                textView.text = text
                textView.textSize = 20f
                textView.textAlignment = View.TEXT_ALIGNMENT_CENTER
                textView.text = text
                if(mode=="icon"){
                    imageView.setImageResource(image_id) // Cambia esto con tu imagen

                    textView.layoutParams = GridLayout.LayoutParams().apply {
                        width = dpWidth.toInt() / numColumns // Mitad del ancho en orientación vertical
                        height = GridLayout.LayoutParams.WRAP_CONTENT // Altura automática
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) // Ajusta la columna automáticamente
                    }

                    imageView.layoutParams = GridLayout.LayoutParams().apply {
                        width = dpWidth.toInt() / numColumns // Mitad del ancho en orientación vertical
                        height = GridLayout.LayoutParams.WRAP_CONTENT // Altura automática
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) // Ajusta la columna automáticamente
                    }
                    //delete vertical space between images
                    imageView.adjustViewBounds = true

                    val linearLayout = GridLayout(requireContext())
                    linearLayout.orientation = GridLayout.VERTICAL
                    linearLayout.addView(imageView)
                    //linearLayout.addView(textView)

                    gridLayout.addView(linearLayout)

                }
                else if (mode=="text"){
                    imageView.setImageResource(R.drawable.ic_empty_button)

                    textView.textSize = 25f
                    //change color to black
                    textView.setTextColor(resources.getColor(R.color.black))
                    //alinear texto al centro vertical y horizontal
                    textView.gravity = Gravity.CENTER
                    //define textview limits to stay inside of the button
                    textView.setPadding(30, 10, 30, 10)


                    // Crea un FrameLayout para superponer imageView y textView
                    val frameLayout = FrameLayout(requireContext())
                    frameLayout.layoutParams = FrameLayout.LayoutParams(
                        dpWidth.toInt() / numColumns,
                        dpWidth.toInt() / numColumns
                    )

                    // Añade imageView y textView al FrameLayout
                    frameLayout.addView(imageView)
                    frameLayout.addView(textView)

                    // Agrega el FrameLayout al gridLayout (que debe ser FrameLayout)
                    gridLayout.addView(frameLayout)



                }
                //add function to imageView with long click
                imageView.setOnLongClickListener {
                    //delete item from database
                    val dbHelper = DatabaseHelper(requireContext())
                    val db = dbHelper.writableDatabase
                    db.execSQL("DELETE FROM TusDatos WHERE _id = $id")
                    db.close()
                    //delete item from gridLayout
                    gridLayout.removeView(it)
                    //update gridLayout
                    UpdateGridLayout()

                    true
                }



            } while (cursor.moveToNext())
        }
        db.close()
    }


    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.shutdown()
    }

}