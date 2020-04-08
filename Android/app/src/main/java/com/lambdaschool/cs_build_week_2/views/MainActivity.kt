package com.lambdaschool.cs_build_week_2.views

import android.content.*
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.view.View
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lambdaschool.cs_build_week_2.R
import com.lambdaschool.cs_build_week_2.api.*
import com.lambdaschool.cs_build_week_2.models.*
import com.lambdaschool.cs_build_week_2.utils.Mining
import com.lambdaschool.cs_build_week_2.utils.SharedPrefs
import com.lambdaschool.cs_build_week_2.utils.UserInteraction
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.OkHttpClient
import okhttp3.internal.toHexString
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.lang.reflect.Type
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


class MainActivity : AppCompatActivity(), SelectionDialog.OnSelectionDialogInteractionListener {

    companion object {
        var traversalTimer = Timer()
        val timerHandlerSpecific = Handler()
        val timerHandlerUnexplored = Handler()
        var cooldownTimer: CountDownTimer? = null
        var cooldownAmount: Double? = 0.0
        var automatedPath: ArrayList<Int?> = arrayListOf()
        var currentRoomId: Int = -1
        var responseMessage: String = ""
        var responseRoomInfo: String = ""
        var inventoryStatus: Status = Status()
        var examineShort: ExamineShort = ExamineShort()
        var traverseToRoom: Int = 309
        var mine: Mine = Mine(-1)
        var proof: Proof = Proof()
        const val baseUrl = "https://lambda-treasure-hunt.herokuapp.com/api/"
        val roomDetails: RoomDetails = RoomDetails()
        val cardinalReference: HashMap<String, String> = hashMapOf(Pair("n", "s"), Pair("s", "n"), Pair("e", "w"), Pair("w", "e"))
        val roomConnections: HashMap<String, Int?> = hashMapOf(Pair("n", null), Pair("s", null), Pair("e", null), Pair("w", null))
        val cellDetails = CellDetails()
        val roomsGraph = HashMap<Int?, ArrayList<Any?>>()
        val darkGraph = HashMap<Int?, ArrayList<Any?>>()
        var inDarkWorld = false
        var authorizationToken: String? = null
        lateinit var preferences: SharedPreferences
        lateinit var preferencesDark: SharedPreferences
        fun initializeCompanion(context: Context) {
            if (!Companion::preferences.isInitialized) {
                preferences = context.getSharedPreferences("RoomsData", Context.MODE_PRIVATE)
            }
            if (!Companion::preferencesDark.isInitialized) {
                preferencesDark = context.getSharedPreferences("DarkData", Context.MODE_PRIVATE)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = "Lambda Treasure Hunt"
        initializeCompanion(this)
        startActivity(Intent(this, InitialActivity::class.java))

        text_room_info.setOnLongClickListener {
            if (isInitDataDownloaded()) {
                val descriptionToCopy: String = if (getCurrentRoomDetails().title == "Wishing Well") {
                    examineShort.description ?: "Nothing copied!"
                } else if (mine.proof != -1) {
                    mine.proof.toString()
                } else {
                    return@setOnLongClickListener false
                }
                val clipboard: ClipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip: ClipData = ClipData.newPlainText("LTH Description", descriptionToCopy)
                clipboard.setPrimaryClip(clip)
                UserInteraction.inform(this, "Copied the description to the clipboard.")
            }
            return@setOnLongClickListener true
        }
        button_move_north.setOnClickListener { moveInDirection("n") }
        button_move_south.setOnClickListener { moveInDirection("s") }
        button_move_east.setOnClickListener { moveInDirection("e") }
        button_move_west.setOnClickListener { moveInDirection("w") }
        button_init.setOnClickListener { networkGetInit() }
        button_traverse.setOnClickListener {
//            moveToUnexploredAutomated(pauseInSeconds = 10)
            moveToSpecificRoomAutomated(traverseToRoom, pauseInSeconds = 6)
        }
        button_take.setOnClickListener {
            if (isInitDataDownloaded()) {
                val roomItems: ArrayList<String> = (getCurrentRoomDetails()).items as ArrayList<String>
                if (roomItems.isNotEmpty()) {
                    showSelectionDialog(roomItems, R.color.colorForest, SelectionDialog.Selections.TAKE)
                } else {
                    UserInteraction.inform(this, "Nothing to take!")
                }
            }
        }
        button_drop.setOnClickListener {
            if (isStatusDataDownloaded()) {
                val inventoryItems: ArrayList<String> = inventoryStatus.inventory?.toCollection(ArrayList()) ?: arrayListOf()
                if (inventoryItems.isNotEmpty()) {
                    showSelectionDialog(inventoryItems, R.color.colorRuby, SelectionDialog.Selections.DROP)
                } else {
                    UserInteraction.inform(this, "Nothing to drop!")
                }
            }
        }
        button_status.setOnClickListener { networkPostStatus() }
        button_buy.setOnClickListener {
            if (isInitDataDownloaded()) {
                var whatToBuy: String = ""
                if (getCurrentRoomDetails().title == "Red Egg Pizza Parlor") {
                    whatToBuy = "Pizza"
                } else if (getCurrentRoomDetails().title == "JKMT Donuts") {
                    whatToBuy = "Donut"
                }
                showSelectionDialog(arrayListOf(whatToBuy), R.color.colorForest, SelectionDialog.Selections.BUY)
            }
        }
        button_sell.setOnClickListener {
            if (isStatusDataDownloaded()) {
                val inventoryItems: ArrayList<String> = inventoryStatus.inventory?.toCollection(ArrayList()) ?: arrayListOf()
                if (inventoryItems.isNotEmpty()) {
                    showSelectionDialog(inventoryItems, R.color.colorRuby, SelectionDialog.Selections.SELL)
                } else {
                    UserInteraction.inform(this, "Nothing to sell!")
                }
            }
        }
        button_wear.setOnClickListener {
            if (isStatusDataDownloaded()) {
                val inventoryItems: ArrayList<String> = inventoryStatus.inventory?.toCollection(ArrayList()) ?: arrayListOf()
                val wearables: ArrayList<String> = arrayListOf()
                val footwear: String = "boots"
                val bodywear: String = "jacket"
                inventoryItems.forEach {
                    if (it.contains(footwear, true) || it.contains(bodywear, true)) {
                        wearables.add(it)
                    }
                }
                if (wearables.isNotEmpty()) {
                    showSelectionDialog(wearables, R.color.colorCupid, SelectionDialog.Selections.WEAR)
                } else {
                    UserInteraction.inform(this, "Nothing to wear!")
                }
            }
        }
        button_undress.setOnClickListener {
            if (isStatusDataDownloaded()) {
                val wearables: ArrayList<String> = arrayListOf()
                val footwear: String = "boots"
                val bodywear: String = "jacket"
                if (inventoryStatus.footwear?.contains(footwear, true) == true) {
                    wearables.add(inventoryStatus.footwear ?: footwear)
                }
                if (inventoryStatus.bodywear?.contains(bodywear, true) == true) {
                    wearables.add(inventoryStatus.bodywear ?: bodywear)
                }
                if (wearables.isNotEmpty()) {
                    showSelectionDialog(wearables, R.color.colorAmber, SelectionDialog.Selections.UNDRESS)
                } else {
                    UserInteraction.inform(this, "Nothing to undress!")
                }
            }
        }
        button_examine.setOnClickListener {
            if (isInitDataDownloaded()) {
                val currentRoomDetails: RoomDetails = getCurrentRoomDetails()
                var whatToExamine: String = ""
                if (currentRoomDetails.title == "Wishing Well") {
                    whatToExamine = "Well"
                } else if (currentRoomDetails.title == "Arron's Athenaeum") {
                    whatToExamine = "Book"
                }
                val combined: ArrayList<String> = arrayListOf()
                if (whatToExamine.isNotEmpty()) {
                    combined.add(whatToExamine)
                }
                val players: ArrayList<String> = (currentRoomDetails).players as ArrayList<String>
                combined.addAll(players)
                val roomItems: ArrayList<String> = (currentRoomDetails).items as ArrayList<String>
                combined.addAll(roomItems)
                val inventoryItems: ArrayList<String> = inventoryStatus.inventory?.toCollection(ArrayList()) ?: arrayListOf()
                combined.addAll(inventoryItems)
                if (combined.isNotEmpty()) {
                    showSelectionDialog(combined, R.color.colorForest, SelectionDialog.Selections.EXAMINE)
                } else {
                    UserInteraction.inform(this, "Nothing to Examine!")
                }
            }
        }
        button_change_name.setOnClickListener {
            //TODO: Allow for a custom input
            networkPostChangeName(Treasure("Basil der Grosse", "aye"))
        }
        button_pray.setOnClickListener {
            networkPostPray()
        }
        button_dash.setOnClickListener {
            val directionsFromRoom: HashMap<String, Int?> = getDirectionsFromRoom(currentRoomId)
            val directionChoices: ArrayList<String> = arrayListOf()
            directionsFromRoom.forEach {
                when (it.key) {
                    "n" -> directionChoices.add("North")
                    "s" -> directionChoices.add("South")
                    "e" -> directionChoices.add("East")
                    "w" -> directionChoices.add("West")
                }
            }
            if (directionChoices.isNotEmpty()) {
                directionChoices.add("Arbitrary")
                showSelectionDialog(directionChoices, R.color.colorSky, SelectionDialog.Selections.DASH)
            } else {
                UserInteraction.inform(this, "Nowhere to dash!")
            }
        }
        button_transmogrify.setOnClickListener {
            val inventoryItems: ArrayList<String> = inventoryStatus.inventory?.toCollection(ArrayList()) ?: arrayListOf()
            if (inventoryItems.isNotEmpty()) {
                showSelectionDialog(inventoryItems, R.color.colorRuby, SelectionDialog.Selections.TRANSMOGRIFY)
            } else {
                UserInteraction.inform(this, "Nothing to transmogrify!")
            }
        }
        button_carry.setOnClickListener {
            val inventoryItems: ArrayList<String> = inventoryStatus.inventory?.toCollection(ArrayList()) ?: arrayListOf()
            if (inventoryItems.isNotEmpty()) {
                showSelectionDialog(inventoryItems, R.color.colorRuby, SelectionDialog.Selections.CARRY)
            } else {
                UserInteraction.inform(this, "Nothing to carry!")
            }
        }
        button_receive.setOnClickListener { networkPostReceive() }
        button_warp.setOnClickListener { networkPostWarp() }
        button_recall.setOnClickListener { networkPostRecall() }
        button_mine.setOnClickListener {
            if (isProofDataDownloaded()) {
                val mine: Mine = Mine(Mining.proofOfWork(proof.proof, proof.difficulty))
                networkPostMine(mine)
            }
        }
        button_last_proof.setOnClickListener { networkGetLastProof() }
        button_get_balance.setOnClickListener { networkGetBalance() }
    }

    private fun showSelectionDialog(list: ArrayList<String>, color: Int, enum: SelectionDialog.Selections) {
        val selectionDialog: SelectionDialog = SelectionDialog()
        val listBundle = Bundle()
        listBundle.putStringArrayList(SelectionDialog.selectionTag, list)
        listBundle.putInt(SelectionDialog.colorTag, color)
        listBundle.putParcelable(SelectionDialog.enumTag, enum)
        selectionDialog.arguments = listBundle
        selectionDialog.isCancelable = false
        selectionDialog.show(supportFragmentManager, SelectionDialog.selectionTag)
    }

    override fun onSelectionDialogInteractionTake(item: String) {
        val roomItems: ArrayList<String> = (getCurrentRoomDetails()).items as ArrayList<String>
        roomItems.remove(item)
        // TODO: Add to Inventory items
        networkPostTakeTreasure(Treasure(item))
    }

    override fun onSelectionDialogInteractionDrop(item: String) {
        val inventoryItems: ArrayList<String> = inventoryStatus.inventory?.toCollection(ArrayList()) ?: arrayListOf()
        inventoryItems.remove(item)
        inventoryStatus.inventory = inventoryItems
        //TODO: Add to Room items
        networkPostDrop(Treasure(item))
    }

    override fun onSelectionDialogInteractionBuy(item: String) {
        //TODO: Add to Inventory items
        networkPostBuyTreasure(Treasure(item, "yes"))
    }

    override fun onSelectionDialogInteractionSell(item: String) {
        val inventoryItems: ArrayList<String> = inventoryStatus.inventory?.toCollection(ArrayList()) ?: arrayListOf()
        inventoryItems.remove(item)
        inventoryStatus.inventory = inventoryItems
        networkPostSellTreasure(Treasure(item, "yes"))
    }

    override fun onSelectionDialogInteractionWear(item: String) {
        val inventoryItems: ArrayList<String> = inventoryStatus.inventory?.toCollection(ArrayList()) ?: arrayListOf()
        inventoryItems.remove(item)
        inventoryStatus.inventory = inventoryItems
        networkPostWear(Treasure(item))
    }

    override fun onSelectionDialogInteractionUndress(item: String) {
        //TODO: Remove from equip
        //TODO: Add to Inventory items
        networkPostUndress(Treasure(item))
    }

    override fun onSelectionDialogInteractionExamine(item: String) {
        if (item.contains("Book", true) || item.contains("Well", true)) {
            networkPostExamineShort(Treasure(item))
        } else {
            networkPostExamineTreasure(Treasure(item))
        }
    }

    override fun onSelectionDialogInteractionDash(item: String) {
        val directionAndPath: HashMap<String, ArrayList<Int?>> =
            hashMapOf(Pair("n", arrayListOf()), Pair("s", arrayListOf()), Pair("e", arrayListOf()), Pair("w", arrayListOf()))
        directionAndPath.forEach { eachDirection ->
            var roomId: Int? = currentRoomId
            do {
                val directionsFromRoom: HashMap<String, Int?> = getDirectionsFromRoom(roomId)
                roomId = directionsFromRoom[eachDirection.key]
                if (roomId != null)
                    directionAndPath[eachDirection.key]?.add(roomId)
            } while (roomId != null)
        }
        val dash: Dash = Dash("", "0", "")
        if (item.equals("Arbitrary", true)) {
            directionAndPath.forEach {
                if (it.value.size > dash.num_rooms.toInt()) {
                    dash.direction = it.key
                    dash.num_rooms = it.value.size.toString()
                    dash.next_room_ids = it.value.joinToString(separator = ",")
                }
            }
        } else {
            val truncatedDirection: String = item[0].toLowerCase().toString()
            dash.direction = truncatedDirection
            dash.num_rooms = directionAndPath[truncatedDirection]?.size.toString()
            dash.next_room_ids = directionAndPath[truncatedDirection]?.joinToString(separator = ",") ?: ""
        }
        networkPostDash(dash)
    }

    override fun onSelectionDialogInteractionTransmogrify(item: String) {
        val inventoryItems: ArrayList<String> = inventoryStatus.inventory?.toCollection(ArrayList()) ?: arrayListOf()
        inventoryItems.remove(item)
        inventoryStatus.inventory = inventoryItems
        networkPostTransmogrify(Treasure(item));
    }

    override fun onSelectionDialogInteractionCarry(item: String) {
        val inventoryItems: ArrayList<String> = inventoryStatus.inventory?.toCollection(ArrayList()) ?: arrayListOf()
        inventoryItems.remove(item)
        inventoryStatus.inventory = inventoryItems
        networkPostCarry(Treasure(item))
    }

    private fun networkGetInit() {
        val retrofit: Retrofit = retrofitGetBuilder()
        val service: InitInterface = retrofit.create(InitInterface::class.java)
        val call: Call<RoomDetails> = service.getRoomInit()
        call.enqueue(object : Callback<RoomDetails> {
            override fun onFailure(call: Call<RoomDetails>, t: Throwable) {
                handleNetworkFailure(t.message)
            }

            override fun onResponse(call: Call<RoomDetails>, response: Response<RoomDetails>) {
                if (response.isSuccessful) {
                    val responseBody: RoomDetails = response.body() as RoomDetails
                    networkResponseSuccess(
                        "Init",
                        responseBody.cooldown,
                        responseBody.toString(),
                        response.code(),
                        responseBody.errors,
                        responseBody.messages
                    )
                    updateGraphDetails(responseBody)
                    SharedPrefs.saveState()
                } else {
                    networkResponseError(response.errorBody().toString(), response.message(), response.code())
                }
                showCooldownTimer()
            }
        })
    }


    private fun networkPostMove(moveWisely: MoveWisely) {
        val retrofit: Retrofit = retrofitPostBuilder()
        val service: MoveInterface = retrofit.create(MoveInterface::class.java)
        val call: Call<RoomDetails> = service.postMove(moveWisely)
        var executeResponse: Response<RoomDetails>? = null
        try {
            executeResponse = call.execute()
        } catch (ioe: IOException) {
            responseMessage = "IOException: ${ioe.message}\n"
        } catch (rte: RuntimeException) {
            responseMessage = "RuntimeException: ${rte.message}\n"
        }
        if (executeResponse?.isSuccessful == true) {
            synchronousResponseSuccess(executeResponse, moveWisely, "Move")
        } else {
            synchronousResponseError(executeResponse)
        }
    }

    private fun networkPostFly(moveWisely: MoveWisely) {
        val retrofit: Retrofit = retrofitPostBuilder()
        val service: FlyInterface = retrofit.create(FlyInterface::class.java)
        val call: Call<RoomDetails> = service.postFly(moveWisely)
        var executeResponse: Response<RoomDetails>? = null
        try {
            executeResponse = call.execute()
        } catch (ioe: IOException) {
            responseMessage = "IOException: ${ioe.message}\n"
        } catch (rte: RuntimeException) {
            responseMessage = "RuntimeException: ${rte.message}\n"
        }
        if (executeResponse?.isSuccessful == true) {
            synchronousResponseSuccess(executeResponse, moveWisely, "Fly")
        } else {
            synchronousResponseError(executeResponse)
        }
    }

    private fun synchronousResponseSuccess(executeResponse: Response<RoomDetails>, moveWisely: MoveWisely, task: String) {
        val originalRoomId: Int = currentRoomId
        val responseAsRoomDetails: RoomDetails = executeResponse.body() as RoomDetails
        responseRoomInfo = responseAsRoomDetails.toString()
        cooldownAmount = responseAsRoomDetails.cooldown
        responseMessage = "Code ${executeResponse.code()}: "
        if (responseAsRoomDetails.errors?.isNotEmpty() == true) {
            responseMessage += "$task failure (\"${moveWisely.direction}\") ${responseAsRoomDetails.errors?.joinToString(
                " ",
                prefix = "\n"
            )?.trim()}"
        } else {
            responseMessage += "$task success! ${responseAsRoomDetails.messages?.joinToString(" ", prefix = "\n")?.trim()}"
            updateGraphDetails(responseAsRoomDetails)
            setRoomIdForPreviousRoom(cardinalReference[moveWisely.direction], originalRoomId)
            SharedPrefs.saveState()
        }
    }

    private fun synchronousResponseError(executeResponse: Response<RoomDetails>?) {
        val errorBodyTypeCast: Type = object : TypeToken<ErrorBody>() {}.type
        val errorBody: ErrorBody = Gson().fromJson(executeResponse?.errorBody()?.string(), errorBodyTypeCast)
        responseMessage = "${executeResponse?.message()} ${executeResponse?.code()}:\n$errorBody"
        cooldownAmount = errorBody.cooldown
    }

    private fun networkPostStatus() {
        val retrofit: Retrofit = retrofitPostBuilder()
        val service: StatusInterface = retrofit.create(StatusInterface::class.java)
        val call: Call<Status> = service.postStatus()
        call.enqueue(object : Callback<Status> {
            override fun onFailure(call: Call<Status>, t: Throwable) {
                handleNetworkFailure(t.message)
            }

            override fun onResponse(call: Call<Status>, response: Response<Status>) {
                if (response.isSuccessful) {
                    val responseBody: Status = response.body() as Status
                    networkResponseSuccess(
                        "Status",
                        responseBody.cooldown,
                        responseBody.toString(),
                        response.code(),
                        responseBody.errors,
                        responseBody.messages
                    )
                    inventoryStatus = responseBody
                } else {
                    networkResponseError(response.errorBody().toString(), response.message(), response.code())
                }
                showCooldownTimer()
            }
        })
    }

    private fun networkPostPray() {
        val retrofit: Retrofit = retrofitPostBuilder()
        val service: PrayInterface = retrofit.create(PrayInterface::class.java)
        val call: Call<RoomDetails> = service.postPray()
        call.enqueue(object : Callback<RoomDetails> {
            override fun onFailure(call: Call<RoomDetails>, t: Throwable) {
                handleNetworkFailure(t.message)
            }

            override fun onResponse(call: Call<RoomDetails>, response: Response<RoomDetails>) {
                if (response.isSuccessful) {
                    val responseBody: RoomDetails = response.body() as RoomDetails
                    networkResponseSuccess(
                        "Pray",
                        responseBody.cooldown,
                        responseBody.toString(),
                        response.code(),
                        responseBody.errors,
                        responseBody.messages
                    )
                } else {
                    networkResponseError(response.errorBody().toString(), response.message(), response.code())
                }
                showCooldownTimer()
            }
        })
    }

    private fun networkPostRecall() {
        val retrofit: Retrofit = retrofitPostBuilder()
        val service: RecallInterface = retrofit.create(RecallInterface::class.java)
        val call: Call<RoomDetails> = service.postRecall()
        call.enqueue(object : Callback<RoomDetails> {
            override fun onFailure(call: Call<RoomDetails>, t: Throwable) {
                handleNetworkFailure(t.message)
            }

            override fun onResponse(call: Call<RoomDetails>, response: Response<RoomDetails>) {
                if (response.isSuccessful) {
                    val responseBody: RoomDetails = response.body() as RoomDetails
                    networkResponseSuccess(
                        "Recall",
                        responseBody.cooldown,
                        responseBody.toString(),
                        response.code(),
                        responseBody.errors,
                        responseBody.messages
                    )
                    updateGraphDetails(responseBody)
                    SharedPrefs.saveState()
                } else {
                    networkResponseError(response.errorBody().toString(), response.message(), response.code())
                }
                showCooldownTimer()
            }
        })
    }

    private fun networkPostBuyTreasure(treasure: Treasure) {
        val retrofit: Retrofit = retrofitPostBuilder()
        val service: BuyInterface = retrofit.create(BuyInterface::class.java)
        val call: Call<RoomDetails> = service.postBuy(treasure)
        call.enqueue(object : Callback<RoomDetails> {
            override fun onFailure(call: Call<RoomDetails>, t: Throwable) {
                handleNetworkFailure(t.message)
            }

            override fun onResponse(call: Call<RoomDetails>, response: Response<RoomDetails>) {
                if (response.isSuccessful) {
                    val responseBody: RoomDetails = response.body() as RoomDetails
                    networkResponseSuccess(
                        "Buy",
                        responseBody.cooldown,
                        responseBody.toString(),
                        response.code(),
                        responseBody.errors,
                        responseBody.messages
                    )
                } else {
                    networkResponseError(response.errorBody().toString(), response.message(), response.code())
                }
                showCooldownTimer()
            }
        })
    }

    private fun networkPostWear(treasure: Treasure) {
        val retrofit: Retrofit = retrofitPostBuilder()
        val service: WearInterface = retrofit.create(WearInterface::class.java)
        val call: Call<Status> = service.postWear(treasure)
        call.enqueue(object : Callback<Status> {
            override fun onFailure(call: Call<Status>, t: Throwable) {
                handleNetworkFailure(t.message)
            }

            override fun onResponse(call: Call<Status>, response: Response<Status>) {
                if (response.isSuccessful) {
                    val responseBody: Status = response.body() as Status
                    networkResponseSuccess(
                        "Wear",
                        responseBody.cooldown,
                        responseBody.toString(),
                        response.code(),
                        responseBody.errors,
                        responseBody.messages
                    )
                    inventoryStatus = responseBody
                } else {
                    networkResponseError(response.errorBody().toString(), response.message(), response.code())
                }
                showCooldownTimer()
            }
        })
    }

    private fun networkPostUndress(treasure: Treasure) {
        val retrofit: Retrofit = retrofitPostBuilder()
        val service: UndressInterface = retrofit.create(UndressInterface::class.java)
        val call: Call<Status> = service.postUndress(treasure)
        call.enqueue(object : Callback<Status> {
            override fun onFailure(call: Call<Status>, t: Throwable) {
                handleNetworkFailure(t.message)
            }

            override fun onResponse(call: Call<Status>, response: Response<Status>) {
                if (response.isSuccessful) {
                    val responseBody: Status = response.body() as Status
                    networkResponseSuccess(
                        "Undress",
                        responseBody.cooldown,
                        responseBody.toString(),
                        response.code(),
                        responseBody.errors,
                        responseBody.messages
                    )
                    inventoryStatus = responseBody
                } else {
                    networkResponseError(response.errorBody().toString(), response.message(), response.code())
                }
                showCooldownTimer()
            }
        })
    }

    private fun networkPostChangeName(treasureName: Treasure) {
        val retrofit: Retrofit = retrofitPostBuilder()
        val service: ChangeNameInterface = retrofit.create(ChangeNameInterface::class.java)
        val call: Call<RoomDetails> = service.postChangeName(treasureName)
        call.enqueue(object : Callback<RoomDetails> {
            override fun onFailure(call: Call<RoomDetails>, t: Throwable) {
                handleNetworkFailure(t.message)
            }

            override fun onResponse(call: Call<RoomDetails>, response: Response<RoomDetails>) {
                if (response.isSuccessful) {
                    val responseBody: RoomDetails = response.body() as RoomDetails
                    networkResponseSuccess(
                        "Change name",
                        responseBody.cooldown,
                        responseBody.toString(),
                        response.code(),
                        responseBody.errors,
                        responseBody.messages
                    )
                } else {
                    networkResponseError(response.errorBody().toString(), response.message(), response.code())
                }
                showCooldownTimer()
            }
        })
    }

    private fun networkPostSellTreasure(treasure: Treasure) {
        val retrofit: Retrofit = retrofitPostBuilder()
        val service: SellInterface = retrofit.create(SellInterface::class.java)
        val call: Call<RoomDetails> = service.postSell(treasure)
        call.enqueue(object : Callback<RoomDetails> {
            override fun onFailure(call: Call<RoomDetails>, t: Throwable) {
                handleNetworkFailure(t.message)
            }

            override fun onResponse(call: Call<RoomDetails>, response: Response<RoomDetails>) {
                if (response.isSuccessful) {
                    val responseBody: RoomDetails = response.body() as RoomDetails
                    networkResponseSuccess(
                        "Sell",
                        responseBody.cooldown,
                        responseBody.toString(),
                        response.code(),
                        responseBody.errors,
                        responseBody.messages
                    )
                } else {
                    networkResponseError(response.errorBody().toString(), response.message(), response.code())
                }
                showCooldownTimer()
            }
        })
    }

    private fun networkPostTransmogrify(treasure: Treasure) {
        val retrofit: Retrofit = retrofitPostBuilder()
        val service: TransmogrifyInterface = retrofit.create(TransmogrifyInterface::class.java)
        val call: Call<RoomDetails> = service.postTransmogrify(treasure)
        call.enqueue(object : Callback<RoomDetails> {
            override fun onFailure(call: Call<RoomDetails>, t: Throwable) {
                handleNetworkFailure(t.message)
            }

            override fun onResponse(call: Call<RoomDetails>, response: Response<RoomDetails>) {
                if (response.isSuccessful) {
                    val responseBody: RoomDetails = response.body() as RoomDetails
                    networkResponseSuccess(
                        "Transmogrify",
                        responseBody.cooldown,
                        responseBody.toString(),
                        response.code(),
                        responseBody.errors,
                        responseBody.messages
                    )
                } else {
                    networkResponseError(response.errorBody().toString(), response.message(), response.code())
                }
                showCooldownTimer()
            }
        })
    }

    private fun networkPostDash(dash: Dash) {
        val retrofit: Retrofit = retrofitPostBuilder()
        val service: DashInterface = retrofit.create(DashInterface::class.java)
        val call: Call<RoomDetails> = service.postDash(dash)
        call.enqueue(object : Callback<RoomDetails> {
            override fun onFailure(call: Call<RoomDetails>, t: Throwable) {
                handleNetworkFailure(t.message)
            }

            override fun onResponse(call: Call<RoomDetails>, response: Response<RoomDetails>) {
                if (response.isSuccessful) {
                    val responseBody: RoomDetails = response.body() as RoomDetails
                    networkResponseSuccess(
                        "Dash",
                        responseBody.cooldown,
                        responseBody.toString(),
                        response.code(),
                        responseBody.errors,
                        responseBody.messages
                    )
                    updateGraphDetails(responseBody)
                    SharedPrefs.saveState()
                } else {
                    networkResponseError(response.errorBody().toString(), response.message(), response.code())
                }
                showCooldownTimer()
            }
        })
    }

    private fun networkPostExamineTreasure(treasure: Treasure) {
        val retrofit: Retrofit = retrofitPostBuilder()
        val service: ExamineInterface = retrofit.create(ExamineInterface::class.java)
        val call: Call<Examine> = service.postExamine(treasure)
        call.enqueue(object : Callback<Examine> {
            override fun onFailure(call: Call<Examine>, t: Throwable) {
                handleNetworkFailure(t.message)
            }

            override fun onResponse(call: Call<Examine>, response: Response<Examine>) {
                if (response.isSuccessful) {
                    val responseBody: Examine = response.body() as Examine
                    networkResponseSuccess(
                        "Examine",
                        responseBody.cooldown,
                        responseBody.toString(),
                        response.code(),
                        responseBody.errors,
                        responseBody.messages
                    )
                } else {
                    networkResponseError(response.errorBody().toString(), response.message(), response.code())
                }
                showCooldownTimer()
            }
        })
    }

    private fun networkPostExamineShort(treasure: Treasure) {
        val retrofit: Retrofit = retrofitPostBuilder()
        val service: ExamineShortInterface = retrofit.create(ExamineShortInterface::class.java)
        val call: Call<ExamineShort> = service.postExamineShort(treasure)
        call.enqueue(object : Callback<ExamineShort> {
            override fun onFailure(call: Call<ExamineShort>, t: Throwable) {
                handleNetworkFailure(t.message)
            }

            override fun onResponse(call: Call<ExamineShort>, response: Response<ExamineShort>) {
                if (response.isSuccessful) {
                    val responseBody: ExamineShort = response.body() as ExamineShort
                    networkResponseSuccess(
                        "Examine",
                        responseBody.cooldown,
                        responseBody.toString(),
                        response.code(),
                        responseBody.errors,
                        responseBody.messages
                    )
                    examineShort = responseBody
                } else {
                    networkResponseError(response.errorBody().toString(), response.message(), response.code())
                }
                showCooldownTimer()
            }
        })
    }

    private fun networkPostDrop(treasure: Treasure) {
        val retrofit: Retrofit = retrofitPostBuilder()
        val service: DropInterface = retrofit.create(DropInterface::class.java)
        val call: Call<RoomDetails> = service.postDrop(treasure)
        call.enqueue(object : Callback<RoomDetails> {
            override fun onFailure(call: Call<RoomDetails>, t: Throwable) {
                handleNetworkFailure(t.message)
            }

            override fun onResponse(call: Call<RoomDetails>, response: Response<RoomDetails>) {
                if (response.isSuccessful) {
                    val responseBody: RoomDetails = response.body() as RoomDetails
                    networkResponseSuccess(
                        "Drop",
                        responseBody.cooldown,
                        responseBody.toString(),
                        response.code(),
                        responseBody.errors,
                        responseBody.messages
                    )
                } else {
                    networkResponseError(response.errorBody().toString(), response.message(), response.code())
                }
                showCooldownTimer()
            }
        })
    }

    private fun networkPostTakeTreasure(treasure: Treasure) {
        val retrofit: Retrofit = retrofitPostBuilder()
        val service: TakeInterface = retrofit.create(TakeInterface::class.java)
        val call: Call<RoomDetails> = service.postTake(treasure)
        call.enqueue(object : Callback<RoomDetails> {
            override fun onFailure(call: Call<RoomDetails>, t: Throwable) {
                handleNetworkFailure(t.message)
            }

            override fun onResponse(call: Call<RoomDetails>, response: Response<RoomDetails>) {
                if (response.isSuccessful) {
                    val responseBody: RoomDetails = response.body() as RoomDetails
                    networkResponseSuccess(
                        "Take",
                        responseBody.cooldown,
                        responseBody.toString(),
                        response.code(),
                        responseBody.errors,
                        responseBody.messages
                    )
                } else {
                    networkResponseError(response.errorBody().toString(), response.message(), response.code())
                }
                showCooldownTimer()
            }
        })
    }

    private fun networkPostCarry(treasure: Treasure) {
        val retrofit: Retrofit = retrofitPostBuilder()
        val service: CarryInterface = retrofit.create(CarryInterface::class.java)
        val call: Call<RoomDetails> = service.postCarry(treasure)
        call.enqueue(object : Callback<RoomDetails> {
            override fun onFailure(call: Call<RoomDetails>, t: Throwable) {
                handleNetworkFailure(t.message)
            }

            override fun onResponse(call: Call<RoomDetails>, response: Response<RoomDetails>) {
                if (response.isSuccessful) {
                    val responseBody: RoomDetails = response.body() as RoomDetails
                    networkResponseSuccess(
                        "Carry",
                        responseBody.cooldown,
                        responseBody.toString(),
                        response.code(),
                        responseBody.errors,
                        responseBody.messages
                    )
                } else {
                    networkResponseError(response.errorBody().toString(), response.message(), response.code())
                }
                showCooldownTimer()
            }
        })
    }

    private fun networkPostReceive() {
        val retrofit: Retrofit = retrofitPostBuilder()
        val service: ReceiveInterface = retrofit.create(ReceiveInterface::class.java)
        val call: Call<RoomDetails> = service.postReceive()
        call.enqueue(object : Callback<RoomDetails> {
            override fun onFailure(call: Call<RoomDetails>, t: Throwable) {
                handleNetworkFailure(t.message)
            }

            override fun onResponse(call: Call<RoomDetails>, response: Response<RoomDetails>) {
                if (response.isSuccessful) {
                    networkResponseSuccess(
                        "Receive",
                        response.body()?.cooldown,
                        response.body().toString(),
                        response.code(),
                        response.body()?.errors,
                        response.body()?.messages
                    )
                } else {
                    networkResponseError(response.errorBody().toString(), response.message(), response.code())
                }
                showCooldownTimer()
            }
        })
    }

    private fun networkPostWarp() {
        val retrofit: Retrofit = retrofitPostBuilder()
        val service: WarpInterface = retrofit.create(WarpInterface::class.java)
        val call: Call<RoomDetails> = service.postWarp()
        call.enqueue(object : Callback<RoomDetails> {
            override fun onFailure(call: Call<RoomDetails>, t: Throwable) {
                handleNetworkFailure(t.message)
            }

            override fun onResponse(call: Call<RoomDetails>, response: Response<RoomDetails>) {
                if (response.isSuccessful) {
                    val responseBody: RoomDetails = response.body() as RoomDetails
                    networkResponseSuccess(
                        "Warp",
                        responseBody.cooldown,
                        responseBody.toString(),
                        response.code(),
                        responseBody.errors,
                        responseBody.messages
                    )
                    updateGraphDetails(responseBody)
                    SharedPrefs.saveState()
                } else {
                    networkResponseError(response.errorBody().toString(), response.message(), response.code())
                }
                showCooldownTimer()
            }
        })
    }

    private fun networkPostMine(mine: Mine) {
        val retrofit: Retrofit = retrofitPostBuilder(30L)
        val service: BcMineInterface = retrofit.create(BcMineInterface::class.java)
        val call: Call<Transaction> = service.postMine(mine)
        call.enqueue(object : Callback<Transaction> {
            override fun onFailure(call: Call<Transaction>, t: Throwable) {
                handleNetworkFailure(t.message)
            }

            override fun onResponse(call: Call<Transaction>, response: Response<Transaction>) {
                if (response.isSuccessful) {
                    val responseBody: Transaction = response.body() as Transaction
                    networkResponseSuccess(
                        "Mine",
                        responseBody.cooldown,
                        responseBody.toString(),
                        response.code(),
                        responseBody.errors,
                        responseBody.messages
                    )
                } else {
                    networkResponseError(response.errorBody().toString(), response.message(), response.code())
                }
                showCooldownTimer()
            }
        })
    }

    private fun networkGetLastProof() {
        val retrofit: Retrofit = retrofitGetBuilder()
        val service: BcLastProofInterface = retrofit.create(BcLastProofInterface::class.java)
        val call: Call<Proof> = service.getLastProof()
        call.enqueue(object : Callback<Proof> {
            override fun onFailure(call: Call<Proof>, t: Throwable) {
                handleNetworkFailure(t.message)
            }

            override fun onResponse(call: Call<Proof>, response: Response<Proof>) {
                if (response.isSuccessful) {
                    val responseBody: Proof = response.body() as Proof
                    networkResponseSuccess(
                        "Last proof",
                        responseBody.cooldown,
                        responseBody.toString(),
                        response.code(),
                        responseBody.errors,
                        responseBody.messages
                    )
                    proof = responseBody
                } else {
                    networkResponseError(response.errorBody().toString(), response.message(), response.code())
                }
                showCooldownTimer()
            }
        })
    }

    private fun networkGetBalance() {
        val retrofit: Retrofit = retrofitGetBuilder()
        val service: BcGetBalanceInterface = retrofit.create(BcGetBalanceInterface::class.java)
        val call: Call<Balance> = service.getBalance()
        call.enqueue(object : Callback<Balance> {
            override fun onFailure(call: Call<Balance>, t: Throwable) {
                handleNetworkFailure(t.message)
            }

            override fun onResponse(call: Call<Balance>, response: Response<Balance>) {
                if (response.isSuccessful) {
                    val responseBody: Balance = response.body() as Balance
                    networkResponseSuccess(
                        "Get balance",
                        responseBody.cooldown,
                        responseBody.toString(),
                        response.code(),
                        responseBody.errors,
                        responseBody.messages
                    )
                } else {
                    networkResponseError(response.errorBody().toString(), response.message(), response.code())
                }
                showCooldownTimer()
            }
        })
    }

    private fun networkResponseSuccess(task: String, time: Double?, str: String, code: Int, errs: List<String>?, msgs: List<String>?) {
        var message: String = "Code $code: "
        message += if (errs?.isNotEmpty() == true) {
            "$task failure! ${errs.joinToString(" ", prefix = "\n").trim()}"
        } else {
            "$task success! ${msgs?.joinToString(" ", prefix = "\n")?.trim()}"
        }
        cooldownAmount = time
        text_room_info.text = str
        text_log.append("$message\n")
        scroll_log.fullScroll(ScrollView.FOCUS_DOWN)
        UserInteraction.inform(applicationContext, message)
    }

    private fun networkResponseError(errorBodyString: String, errorMessage: String, errorCode: Int) {
        val errorBodyTypeCast: Type = object : TypeToken<ErrorBody>() {}.type
        val errorBody: ErrorBody = Gson().fromJson(errorBodyString, errorBodyTypeCast)
        val errorText = "$errorMessage $errorCode:\n$errorBody"
        cooldownAmount = errorBody.cooldown
        text_log.append("$errorText\n")
        scroll_log.fullScroll(ScrollView.FOCUS_DOWN)
        UserInteraction.inform(applicationContext, errorText)
    }

    private fun handleNetworkFailure(failureMessage: String?) {
        text_log.append("$failureMessage\n")
        scroll_log.fullScroll(ScrollView.FOCUS_DOWN)
        UserInteraction.inform(applicationContext, failureMessage ?: "Failure")
    }

    private fun retrofitGetBuilder(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Token $authorizationToken").build()
                chain.proceed(request)
            }.build())
            .build()
    }

    private fun retrofitPostBuilder(timeout: Long = 10L): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(OkHttpClient.Builder().readTimeout(timeout, TimeUnit.SECONDS).addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Token $authorizationToken").build()
                chain.proceed(request)
            }.build())
            .build()
    }

    private fun moveInDirection(direction: String) {
        if (isInitDataDownloaded()) {
            val nextRoom: String? = anticipateNextRoom(direction)
            val moveWisely: MoveWisely = MoveWisely(direction, nextRoom)
            val networkRunnable: Runnable = Runnable {
                if (getCurrentRoomDetails().terrain?.toLowerCase(Locale.getDefault()) == "mountain")
                    networkPostFly(moveWisely)
                else
                    networkPostMove(moveWisely)
            }
            val networkThread = Thread(networkRunnable)
            networkThread.start()
            networkThread.join()
            text_room_info.text = responseRoomInfo
            text_log.append("$responseMessage\n")
            scroll_log.fullScroll(ScrollView.FOCUS_DOWN)
            UserInteraction.inform(applicationContext, responseMessage)
            showCooldownTimer()
        }
    }

    private fun updateGraphDetails(responseBody: RoomDetails?) {
        currentRoomId = responseBody?.roomId ?: 0
        setDarkWorldStatus()
        if (inDarkWorld) {
            if (darkGraph[currentRoomId].isNullOrEmpty())
                darkGraph[currentRoomId] = arrayListOf<Any?>(roomDetails, roomConnections, cellDetails)
            darkGraph[currentRoomId]?.set(0, responseBody)
            darkGraph[currentRoomId]?.set(1, validateRoomConnections(currentRoomId))
            darkGraph[currentRoomId]?.set(2, fillCellDetails(currentRoomId))
        } else {
            if (roomsGraph[currentRoomId].isNullOrEmpty())
                roomsGraph[currentRoomId] = arrayListOf<Any?>(roomDetails, roomConnections, cellDetails)
            roomsGraph[currentRoomId]?.set(0, responseBody)
            roomsGraph[currentRoomId]?.set(1, validateRoomConnections(currentRoomId))
            roomsGraph[currentRoomId]?.set(2, fillCellDetails(currentRoomId))
        }
        view_map.calculateSize()
    }

    private fun setDarkWorldStatus() {
        inDarkWorld = currentRoomId >= 500
        swapWorldAssets()
    }

    private fun swapWorldAssets() {
        if (inDarkWorld) {
            layout_main.background = getDrawable(R.drawable.stone_dark)
        } else {
            layout_main.background = getDrawable(R.drawable.stone)
        }
    }

    private fun anticipateNextRoom(direction: String): String? {
        val directionAssociations: HashMap<*, *> = if (inDarkWorld) {
            darkGraph[currentRoomId]?.get(1) as HashMap<*, *>
        } else {
            roomsGraph[currentRoomId]?.get(1) as HashMap<*, *>
        }
        return (directionAssociations[direction] as Int?)?.toString()
    }

    private fun setRoomIdForPreviousRoom(direction: String?, roomId: Int?) {
        if (currentRoomId != roomId) {
            if (inDarkWorld) {
                @Suppress("UNCHECKED_CAST")
                (darkGraph[currentRoomId]!![1] as HashMap<String, Int?>)[direction ?: "n"] = roomId
            } else {
                @Suppress("UNCHECKED_CAST")
                (roomsGraph[currentRoomId]!![1] as HashMap<String, Int?>)[direction ?: "n"] = roomId
            }
        }
    }

    private fun getDirectionsFromRoom(roomId: Int?): HashMap<String, Int?> {
        if (roomId == null)
            return hashMapOf()
        val roomTrifecta: ArrayList<Any?> = if (inDarkWorld) {
            darkGraph[roomId] ?: arrayListOf()
        } else {
            roomsGraph[roomId] ?: arrayListOf()
        }
        @Suppress("UNCHECKED_CAST")
        return roomTrifecta[1] as HashMap<String, Int?>
    }

    private fun getDirectionForRoom(roomId: Int?): String? {
        val roomDirections: HashMap<String, Int?> = getDirectionsFromRoom(currentRoomId)
        val directions: Set<String> = roomDirections.filterValues { it == roomId }.keys
        return if (directions.isNotEmpty())
            directions.first()
        else
            null
    }

    private fun validateRoomConnections(roomId: Int?): HashMap<String, Int?> {
        val extractedRoomDetailsExits: List<String>?
        val extractedRoomConnections: HashMap<*, *>
        if (inDarkWorld) {
            extractedRoomDetailsExits = (darkGraph[roomId]?.get(0) as RoomDetails).exits
            extractedRoomConnections = darkGraph[roomId]?.get(1) as HashMap<*, *>
        } else {
            extractedRoomDetailsExits = (roomsGraph[roomId]?.get(0) as RoomDetails).exits
            extractedRoomConnections = roomsGraph[roomId]?.get(1) as HashMap<*, *>
        }
        val validExits: HashMap<String, Int?> = hashMapOf()
        extractedRoomDetailsExits?.forEach {
            validExits[it] = extractedRoomConnections[it] as Int?
        }
        return validExits
    }

    private fun fillCellDetails(roomId: Int?): CellDetails {
        val extractedRoomDetails: RoomDetails = if (inDarkWorld) {
            darkGraph[roomId]?.get(0) as RoomDetails
        } else {
            roomsGraph[roomId]?.get(0) as RoomDetails
        }
        val coordinates: String = extractedRoomDetails.coordinates ?: "(0,0)"
        val coordinatesSplit: List<String> = coordinates.substring(1, coordinates.length - 1).split(",")
        val cellColor: String = Color.TRANSPARENT.toHexString()
        return CellDetails(coordinatesSplit[0].toInt(), coordinatesSplit[1].toInt(), cellColor)
    }

    private fun showCooldownTimer() {
        cooldownTimer?.cancel()
        frame_cooldown.visibility = View.VISIBLE
        cooldownTimer = object : CountDownTimer((cooldownAmount?.times(1000))?.toLong() ?: 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val timerText = "CD: " + "${(millisUntilFinished / 1000)}".padStart(2, '0')
                text_cooldown.text = timerText
            }

            override fun onFinish() {
                frame_cooldown.visibility = View.INVISIBLE
                cooldownAmount = 0.0
            }
        }
        cooldownTimer?.start()
    }

    private fun runNextAutomatedStep(keepGoing: Boolean) {
        if (keepGoing)
            timerHandlerUnexplored.post(myRunnableUnexplored)
        else
            timerHandlerSpecific.post(myRunnableSpecific)
    }

    @OptIn(ExperimentalStdlibApi::class)
    val myRunnableUnexplored = Runnable {
        if (automatedPath.isNotEmpty()) {
            var direction: String? = getDirectionForRoom(automatedPath.removeFirst())
            if (direction == null) {
                direction = getCurrentRoomDetails().exits?.random()
            }
            when (direction) {
                "n" -> button_move_north.performClick()
                "s" -> button_move_south.performClick()
                "e" -> button_move_east.performClick()
                "w" -> button_move_west.performClick()
            }
        } else {
            UserInteraction.inform(this, "There is nowhere to go...")
            traversalTimer.cancel()
            return@Runnable
        }
        if (automatedPath.isEmpty()) {
            traversalTimer.cancel()
        }
        if (automatedPath.isEmpty()) {
            moveToUnexploredAutomated()
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    val myRunnableSpecific = Runnable {
        /*if (automatedPath.isEmpty()) {
            UserInteraction.inform(this, "I'm lost...")
            traversalTimer.cancel()
            return@Runnable
        }*/
        val destinationRoom: Int? = automatedPath.last()
        val direction: String? = getDirectionForRoom(automatedPath.removeFirst())
        if (direction == null) {
            UserInteraction.askQuestion(
                this, "Room Not Found", "Problem encountered traversing to room #$destinationRoom!", "Okay", null
            )
            traversalTimer.cancel()
        } else {
            when (direction) {
                "n" -> button_move_north.performClick()
                "s" -> button_move_south.performClick()
                "e" -> button_move_east.performClick()
                "w" -> button_move_west.performClick()
            }
        }
        if (automatedPath.isEmpty()) {
            traversalTimer.cancel()
        }
        val exploredWisely: Boolean = getCurrentRoomDetails().messages?.last()?.contains("Wise") ?: false
        if ((destinationRoom == currentRoomId) || (destinationRoom == null && !exploredWisely)) {
            UserInteraction.askQuestion(this, "Room Found", "Room #$destinationRoom has been found!", "Okay", null)
        }
    }

    private fun getCurrentRoomDetails(): RoomDetails {
        return if (inDarkWorld) {
            darkGraph[currentRoomId]?.get(0) as RoomDetails
        } else {
            roomsGraph[currentRoomId]?.get(0) as RoomDetails
        }
    }

    private fun moveToUnexploredAutomated(pauseInSeconds: Int = 16) {
        if (!isInitDataDownloaded()) return
        automatedPath = bfs(null)
        traversalTimer = Timer()
        traversalTimer.schedule(object : TimerTask() {
            override fun run() {
                runNextAutomatedStep(true)
            }
        }, pauseInSeconds * 1000L, pauseInSeconds * 1000L)
    }

    private fun isInitDataDownloaded(): Boolean {
        if ((inDarkWorld && darkGraph.isEmpty()) || (!inDarkWorld && roomsGraph.isEmpty()) || (currentRoomId == -1)) {
            UserInteraction.inform(this, "Please do a GET 'Init' first...")
            return false
        }
        return true
    }

    private fun isStatusDataDownloaded(): Boolean {
        if (inventoryStatus.name == null) {
            UserInteraction.inform(this, "Please do a POST 'Status' first...")
            return false
        }
        return true
    }

    private fun isProofDataDownloaded(): Boolean {
        if (proof.proof == null) {
            UserInteraction.inform(this, "Please do a GET 'Last proof' first...")
            return false
        }
        return true
    }

    private fun moveToSpecificRoomAutomated(roomId: Int?, pauseInSeconds: Int = 16) {
        if (!isInitDataDownloaded()) {
            return
        } else if (roomId == currentRoomId) {
            UserInteraction.askQuestion(this, "Room Is Here", "You are already at Room #${roomId}!", "Okay", null)
            return
        }
        automatedPath = bfs(roomId)
        traversalTimer = Timer()
        traversalTimer.schedule(object : TimerTask() {
            override fun run() {
                runNextAutomatedStep(false)
            }
        }, 0, pauseInSeconds * 1000L)
    }

    private fun bfs(destinationRoom: Int?): ArrayList<Int?> {
        val queue: Queue<ArrayList<Int?>> = LinkedList()
        queue.add(arrayListOf(currentRoomId))
        val contemplated: MutableSet<Int?> = mutableSetOf()
        while (queue.isNotEmpty()) {
            val path: ArrayList<Int?> = queue.remove()
            val room: Int? = path.last()
            if (!contemplated.contains(room)) {
                contemplated.add(room)
                if (room == destinationRoom) {
                    return ArrayList(path.subList(1, path.size))
                }
                getDirectionsFromRoom(room).values.forEach {
                    val pathCopy: ArrayList<Int?> = path.toCollection(arrayListOf())
                    pathCopy.add(it)
                    queue.add(pathCopy)
                }
            }
        }
        return arrayListOf()
    }
}