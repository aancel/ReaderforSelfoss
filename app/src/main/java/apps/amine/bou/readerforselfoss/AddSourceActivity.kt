package apps.amine.bou.readerforselfoss

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.constraint.ConstraintLayout
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import apps.amine.bou.readerforselfoss.api.selfoss.SelfossApi
import apps.amine.bou.readerforselfoss.api.selfoss.Spout
import apps.amine.bou.readerforselfoss.api.selfoss.SuccessResponse
import apps.amine.bou.readerforselfoss.utils.Config
import apps.amine.bou.readerforselfoss.utils.isBaseUrlValid
import com.ftinc.scoop.Scoop
import kotlinx.android.synthetic.main.activity_add_source.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AddSourceActivity : AppCompatActivity() {

    private var mSpoutsValue: String? = null
    private lateinit var api: SelfossApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Scoop.getInstance().apply(this)
        setContentView(R.layout.activity_add_source)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            api = SelfossApi(
                    this,
                    this@AddSourceActivity,
                    prefs.getBoolean("isSelfSignedCert", false),
                    prefs.getBoolean("should_log_everything", false)
            )
        } catch (e: IllegalArgumentException) {
            mustLoginToAddSource()
        }

        maybeGetDetailsFromIntentSharing(intent, sourceUri, nameInput)

        saveBtn.setOnClickListener {
            handleSaveSource(tags, nameInput.text.toString(), sourceUri.text.toString(), api!!)
        }
    }

    override fun onResume() {
        super.onResume()
        val config = Config(this)

        if (config.baseUrl.isEmpty() || !config.baseUrl.isBaseUrlValid()) {
            mustLoginToAddSource()
        } else {
            handleSpoutsSpinner(spoutsSpinner, api, progress, formContainer)
        }
    }

    private fun handleSpoutsSpinner(
            spoutsSpinner: Spinner,
            api: SelfossApi?,
            mProgress: ProgressBar,
            formContainer: ConstraintLayout
    ) {
        val spoutsKV = HashMap<String, String>()
        spoutsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>, view: View?, i: Int, l: Long) {
                if (view != null) {
                    val spoutName = (view as TextView).text.toString()
                    mSpoutsValue = spoutsKV[spoutName]
                }
            }

            override fun onNothingSelected(adapterView: AdapterView<*>) {
                mSpoutsValue = null
            }
        }

        var items: Map<String, Spout>
        api!!.spouts().enqueue(object : Callback<Map<String, Spout>> {
            override fun onResponse(
                    call: Call<Map<String, Spout>>,
                    response: Response<Map<String, Spout>>
            ) {
                if (response.body() != null) {
                    items = response.body()!!

                    val itemsStrings = items.map { it.value.name }
                    for ((key, value) in items) {
                        spoutsKV.put(value.name, key)
                    }

                    mProgress.visibility = View.GONE
                    formContainer.visibility = View.VISIBLE

                    val spinnerArrayAdapter =
                            ArrayAdapter(
                                    this@AddSourceActivity,
                                    android.R.layout.simple_spinner_item,
                                    itemsStrings
                            )
                    spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spoutsSpinner.adapter = spinnerArrayAdapter
                } else {
                    handleProblemWithSpouts()
                }
            }

            override fun onFailure(call: Call<Map<String, Spout>>, t: Throwable) {
                handleProblemWithSpouts()
            }

            private fun handleProblemWithSpouts() {
                Toast.makeText(
                        this@AddSourceActivity,
                        R.string.cant_get_spouts,
                        Toast.LENGTH_SHORT
                ).show()
                mProgress.visibility = View.GONE
            }
        })
    }

    private fun maybeGetDetailsFromIntentSharing(
            intent: Intent,
            sourceUri: EditText,
            nameInput: EditText
    ) {
        if (Intent.ACTION_SEND == intent.action && "text/plain" == intent.type) {
            sourceUri.setText(intent.getStringExtra(Intent.EXTRA_TEXT))
            nameInput.setText(intent.getStringExtra(Intent.EXTRA_TITLE))
        }
    }

    private fun mustLoginToAddSource() {
        Toast.makeText(this, getString(R.string.addStringNoUrl), Toast.LENGTH_SHORT).show()
        val i = Intent(this, LoginActivity::class.java)
        startActivity(i)
        finish()
    }

    private fun handleSaveSource(tags: EditText, title: String, url: String, api: SelfossApi) {

        val sourceDetailsAvailable = title.isEmpty() || url.isEmpty() || mSpoutsValue == null || mSpoutsValue!!.isEmpty()

        if (sourceDetailsAvailable) {
            Toast.makeText(this, R.string.form_not_complete, Toast.LENGTH_SHORT).show()
        } else {
            api.createSource(
                    title,
                    url,
                    mSpoutsValue!!,
                    tags.text.toString(),
                    ""
            ).enqueue(object : Callback<SuccessResponse> {
                override fun onResponse(
                        call: Call<SuccessResponse>,
                        response: Response<SuccessResponse>
                ) {
                    if (response.body() != null && response.body()!!.isSuccess) {
                        finish()
                    } else {
                        Toast.makeText(
                                this@AddSourceActivity,
                                R.string.cant_create_source,
                                Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<SuccessResponse>, t: Throwable) {
                    Toast.makeText(
                            this@AddSourceActivity,
                            R.string.cant_create_source,
                            Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }
    }
}
