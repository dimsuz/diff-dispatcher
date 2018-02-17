package com.dimsuz.diffdispatcher.sample

import com.dimsuz.diffdispatcher.annotations.DiffElement
import com.dimsuz.diffdispatcher.sample.UserInfoViewState.Interest

@DiffElement(diffReceiver = UserInfoRenderer::class)
data class UserInfoViewState(
    val firstName: String,
    val lastName: String,
    val middleName: String?,
    val address: Address,
    val friends: List<UserInfoViewState>,
    val interests: List<Interest>?,
    val age: Int,
    val popularity: Float,
    val likesCheese: Boolean,
    val isIntelligent: Boolean?
) {
    data class Address(
        val street: String,
        val house: Int
    )
    data class Interest(
        val name: String,
        val tags: List<String>
    )
}

interface UserInfoRenderer {
    fun renderName(firstName: String, middleName: String?, lastName: String)
    fun renderFirstName(firstName: String)
    fun renderUserAddress(address: UserInfoViewState.Address)
    fun renderFriends(friends: List<UserInfoViewState>)
    fun renderFriendsAndInterests(friends: List<UserInfoViewState>, interests: List<Interest>?)
    fun renderPopularity(popularity: Float)
    fun renderAgeAndCheesePreference(age: Int, likesCheese: Boolean)
//    fun fault(isIntelligent: Int)
//    fun fault(popularity: Long)
//    fun fault1(popularity: List<String>)
}

interface UserInfoRenderDispatcher {
    fun render(newState: UserInfoViewState, previousState: UserInfoViewState?)
}

class SampleRenderer : UserInfoRenderer {

    override fun renderName(firstName: String, middleName: String?, lastName: String) {
        println("rendering name $lastName $firstName $middleName")
    }

    override fun renderFirstName(firstName: String) {
        println("rendering first name $firstName")
    }

    override fun renderUserAddress(address: UserInfoViewState.Address) {
        println("rendering address, street  ${address.street} ${address.house}")
    }

    override fun renderFriends(friends: List<UserInfoViewState>) {
        println("rendering friend list $friends")
    }

    override fun renderFriendsAndInterests(friends: List<UserInfoViewState>, interests: List<Interest>?) {
        println("rendering friend list $friends AND interest list $interests")
    }

    override fun renderPopularity(popularity: Float) {
        println("render popularity is $popularity")
    }

    override fun renderAgeAndCheesePreference(age: Int, likesCheese: Boolean) {
        println("render age and cheese: age is $age, likesCheese is $likesCheese")
    }
}

class UserInfoRenderDispatcher_Generated(private val renderer: UserInfoRenderer) : UserInfoRenderDispatcher {
    override fun render(newState: UserInfoViewState, previousState: UserInfoViewState?) {
        if (previousState == null) {
            renderer.renderName(newState.firstName, newState.middleName, newState.lastName)
            renderer.renderFirstName(newState.firstName)
            renderer.renderUserAddress(newState.address)
            renderer.renderFriends(newState.friends)
            renderer.renderFriendsAndInterests(newState.friends, newState.interests)
            renderer.renderPopularity(newState.popularity)
        } else {
            val firstNameChanged = newState.firstName != previousState.firstName
            if (firstNameChanged
                || newState.middleName != previousState.middleName
                || newState.lastName != previousState.lastName) {
                renderer.renderName(newState.firstName, newState.middleName, newState.lastName)
            }

            if (firstNameChanged) {
                renderer.renderFirstName(newState.firstName)
            }

            if (newState.address != previousState.address) {
                renderer.renderUserAddress(newState.address)
            }

            val friendsChanged = newState.friends != previousState.friends
            if (friendsChanged) {
                renderer.renderFriends(newState.friends)
            }

            if (friendsChanged
                || newState.interests != previousState.interests) {
                renderer.renderFriendsAndInterests(newState.friends, newState.interests)
            }

            // in Java must use Float.floatToIntBits() to compare
            if (newState.popularity != previousState.popularity) {
                renderer.renderPopularity(newState.popularity)
            }

            if (newState.age != previousState.age || newState.likesCheese != previousState.likesCheese) {
                renderer.renderAgeAndCheesePreference(newState.age, newState.likesCheese)
            }
        }
    }
}

// Generated too
class UserInfoRenderDispatcherBuilder {
    private var target: UserInfoRenderer? = null

    fun target(t: UserInfoRenderer): UserInfoRenderDispatcherBuilder {
        target = t
        return this
    }

    fun build(): UserInfoRenderDispatcher {
        check(target != null)
        return UserInfoRenderDispatcher_Generated(target!!)
    }
}

fun main(args: Array<String>) {
    val user1 = UserInfoViewState(
        firstName = "Dmitry",
        lastName = "Suzdalev",
        middleName = "Konstantinovich",
        address = UserInfoViewState.Address("Epronovskaya", 21),
        friends = listOf(/* too lazy to create another one here */),
        interests = listOf(Interest("Haskell", listOf("Programming language", "Functional Programming"))),
        popularity = 0.7f,
        age = 30,
        likesCheese = true,
        isIntelligent = null
    )
    val user2 = UserInfoViewState(
        firstName = "Dmitry",
        lastName = "Suzdalev",
        middleName = "Konstantinovich",
        address = UserInfoViewState.Address("Epronovskaya", 28),
        friends = listOf(/* too lazy to create another one here */),
        interests = listOf(Interest("Kotlin", listOf("Programming language"))),
        popularity = 0.8f,
        age = 30,
        likesCheese = true,
        isIntelligent = true
    )

    val dispatcher = UserInfoRenderDispatcherBuilder()
        .target(SampleRenderer())
        .build()

    dispatcher.render(user1, user2)
}