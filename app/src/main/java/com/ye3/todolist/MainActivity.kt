package com.ye3.todolist

import android.app.Activity
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.activity.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.ye3.todolist.databinding.ActivityMainBinding
import com.ye3.todolist.databinding.ItemListBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding : ActivityMainBinding
    private val viewModel : MainViewModel by viewModels()
    val RC_SIGN_IN = 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 로그인 안됨
        if(FirebaseAuth.getInstance().currentUser == null) {
            login()
        }

        binding.addBtn.setOnClickListener{
            val todo = Todo(binding.addText.text.toString())
            viewModel.addTodo(todo)
            binding.addText.setText("")
        }

        binding.listRecycler.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = TodoAdapter(
                emptyList(),
                onClickItem =  {
                    viewModel.toggleTodo(it)
                },
                onClickDeleteIcon = {
                    viewModel.deleteTodo(it)
                })
        }

        // 관찰 UI 업데이트
        viewModel.todoLiveData.observe(this, Observer {
            (binding.listRecycler.adapter as TodoAdapter).setData(it)
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val response = IdpResponse.fromResultIntent(data)

            if (resultCode == Activity.RESULT_OK) {
                // 로그인 성공
                viewModel.fetchData()
            } else {
                // 로그인 실패
                finish()
            }
        }
    }

    fun login() {
        val providers = arrayListOf(AuthUI.IdpConfig.EmailBuilder().build())

        startActivityForResult(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(providers)
                .build(),
            RC_SIGN_IN
        )
    }

    fun logout() {
        AuthUI.getInstance()
            .signOut(this)
            .addOnCompleteListener {
                login()
            }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater : MenuInflater = menuInflater
        inflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

data class Todo(val text : String, var isDone : Boolean = false)

class TodoAdapter(
    private var dataList : List<DocumentSnapshot>,
    val onClickDeleteIcon : (todo : DocumentSnapshot) -> Unit,
    val onClickItem : (todo : DocumentSnapshot) -> Unit
) : RecyclerView.Adapter<TodoAdapter.TodoViewHolder>() {

    class TodoViewHolder(val binding: ItemListBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_list, parent, false)
        return TodoViewHolder(ItemListBinding.bind(view))
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        val todo = dataList[position]
        holder.binding.todoText.text = todo.getString("text") ?: ""

        if(todo.getBoolean("isDone") ?: false) {
            holder.binding.todoText.apply {
                paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                setTypeface(null, Typeface.ITALIC)
            }
        } else {
            holder.binding.todoText.apply {
                paintFlags = 0
                setTypeface(null, Typeface.NORMAL)
            }
        }

        holder.binding.todoDel.setOnClickListener{
            onClickDeleteIcon.invoke(todo)
        }

        holder.binding.todoText.setOnClickListener{
            onClickItem.invoke(todo)
        }
    }

    override fun getItemCount(): Int {
        return dataList.size
    }

    fun setData(newData : List<DocumentSnapshot>) {
        dataList = newData
        notifyDataSetChanged()
    }
}

class MainViewModel : ViewModel() {
    val db = Firebase.firestore
    val todoLiveData = MutableLiveData<List<DocumentSnapshot>>()
    private val data = arrayListOf<QueryDocumentSnapshot>()

    init {
        fetchData()
    }

    fun fetchData() {
        val user = FirebaseAuth.getInstance().currentUser
        if(user != null) {
            db.collection(user.uid)
                .addSnapshotListener { value, e ->
                    // 에러일 때 종료
                    if (e != null) {
                        return@addSnapshotListener
                    }
                    if(value != null) {
                        todoLiveData.value = value.documents
                    }
                    data.clear()
                    for (document in value!!) {
                        data.add(document)
                    }
                    todoLiveData.value = data
                }
        }
    }

    fun addTodo(todo : Todo){
        FirebaseAuth.getInstance().currentUser?.let { user ->
            db.collection(user.uid).add(todo)
        }
    }

    fun deleteTodo(todo: DocumentSnapshot) {
        FirebaseAuth.getInstance().currentUser?.let { user ->
            db.collection(user.uid).document(todo.id).delete()
        }
    }

    fun toggleTodo(todo: DocumentSnapshot){
        FirebaseAuth.getInstance().currentUser?.let { user ->
            val isDone = todo.getBoolean("isDone") ?: false
            db.collection(user.uid).document(todo.id).update("isDone", !isDone)
        }
    }

}