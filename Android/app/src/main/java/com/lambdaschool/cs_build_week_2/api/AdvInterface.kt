package com.lambdaschool.cs_build_week_2.api

import com.lambdaschool.cs_build_week_2.models.*
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface InitInterface {
    @GET("adv/init/")
    fun getRoomInit(): Call<RoomDetails>
}

interface MoveInterface {
    @POST("adv/move/")
    fun postMove(@Body moveWisely: MoveWisely): Call<RoomDetails>
}

interface TakeInterface {
    @POST("adv/take/")
    fun postTake(@Body treasure: Treasure): Call<RoomDetails>
}

interface DropInterface {
    @POST("adv/drop/")
    fun postDrop(@Body treasure: Treasure): Call<RoomDetails>
}

interface StatusInterface {
    @POST("adv/status/")
    fun postStatus(): Call<Status>
}

interface BuyInterface {
    @POST("adv/buy/")
    fun postBuy(@Body treasure: Treasure): Call<RoomDetails>
}

interface SellInterface {
    @POST("adv/sell/")
    fun postSell(@Body treasure: Treasure): Call<RoomDetails>
}

interface WearInterface {
    @GET("adv/wear/")
    fun getWear(): Call<RoomDetails>
}

interface UndressInterface {
    @GET("adv/undress/")
    fun getUndress(): Call<RoomDetails>
}

interface ExamineInterface {
    @POST("adv/examine/")
    fun postExamine(@Body treasure: Treasure): Call<RoomDetails>
}

interface ExamineShortInterface {
    @POST("adv/examine/")
    fun postExamineShort(@Body treasure: Treasure): Call<ExamineShort>
}

interface ChangeNameInterface {
    @POST("adv/change_name/")
    fun postChangeName(@Body treasure: Treasure): Call<RoomDetails>
}

interface PrayInterface {
    @POST("adv/pray/")
    fun postPray(): Call<RoomDetails>
}

interface FlyInterface {
    @POST("adv/fly/")
    fun postFly(@Body moveWisely: MoveWisely): Call<RoomDetails>
}

interface DashInterface {
    @GET("adv/dash/")
    fun getDash(): Call<RoomDetails>
}

interface PlayerStateInterface {
    @GET("adv/player_state/")
    fun getPlayerState(): Call<RoomDetails>
}

interface TransmogrifyInterface {
    @GET("adv/transmogrify/")
    fun getTransmogrify(): Call<RoomDetails>
}

interface CarryInterface {
    @GET("adv/carry/")
    fun getCarry(): Call<RoomDetails>
}

interface ReceiveInterface {
    @GET("adv/receive/")
    fun getReceive(): Call<RoomDetails>
}

interface WarpInterface {
    @GET("adv/warp/")
    fun getWarp(): Call<RoomDetails>
}

interface RecallInterface {
    @GET("adv/recall/")
    fun getRecall(): Call<RoomDetails>
}
