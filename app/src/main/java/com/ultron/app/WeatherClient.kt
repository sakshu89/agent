package com.ultron.app
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import android.os.Handler
import android.os.Looper
import java.util.concurrent.TimeUnit
class WeatherClient(private val context:Context){
 private val main=Handler(Looper.getMainLooper())
 private val client=OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(10,TimeUnit.SECONDS).build()
 fun current(onResult:(String)->Unit,onError:(String)->Unit){
  val done:(String)->Unit={text->main.post{onResult(text)}}
  val fail:(String)->Unit={text->main.post{onError(text)}}
  val lm=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
  if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){fail("Location permission is required for local weather.");return}
  val loc=listOf(LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER).asSequence().mapNotNull{runCatching{lm.getLastKnownLocation(it)}.getOrNull()}.maxByOrNull{it.time}
  if(loc==null){fail("I need a recent location fix before I can check local weather.");return}
  Thread{try{
   val url="https://api.open-meteo.com/v1/forecast?latitude=${loc.latitude}&longitude=${loc.longitude}&current=temperature_2m,apparent_temperature,rain,weather_code,wind_speed_10m&timezone=auto"
   val body=client.newCall(Request.Builder().url(url).header("User-Agent","ITRA/8.0 Android").build()).execute().use{r-> if(!r.isSuccessful)throw IllegalStateException("Weather service HTTP ${r.code}"); r.body?.string().orEmpty()}
   val c=JSONObject(body).getJSONObject("current"); val t=c.getDouble("temperature_2m");val f=c.getDouble("apparent_temperature");val w=c.getDouble("wind_speed_10m");val rain=c.getDouble("rain");val code=c.getInt("weather_code")
   done("${label(code)}. ${"%.0f".format(t)}°C, feels like ${"%.0f".format(f)}°C, wind ${"%.0f".format(w)} km/h. Rain right now: ${"%.1f".format(rain)} mm.")
  }catch(t:Throwable){fail("I couldn't reach the weather service: ${t.message?:"unknown error"}")}}.start()
 }
 private fun label(c:Int)=when(c){0->"Clear skies";1,2->"Mostly clear";3->"Overcast";45,48->"Foggy";51,53,55,56,57->"Drizzle";61,63,65,66,67->"Rain";71,73,75,77->"Snow";80,81,82->"Rain showers";85,86->"Snow showers";95,96,99->"Thunderstorms";else->"Mixed conditions"}
}
