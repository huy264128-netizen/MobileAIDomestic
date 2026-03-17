//package com.projectmaidgroup.mobileaidomestic
//
//import androidx.compose.foundation.Image
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.Row
//import androidx.compose.foundation.layout.Spacer
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.height
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.layout.size
//import androidx.compose.foundation.layout.width
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.automirrored.filled.Send
//import androidx.compose.material.icons.filled.*
//import androidx.compose.material3.BottomAppBar
//import androidx.compose.material3.BottomAppBarDefaults
//import androidx.compose.material3.ButtonDefaults
//import androidx.compose.material3.FloatingActionButton
//import androidx.compose.material3.FloatingActionButtonDefaults
//import androidx.compose.material3.Icon
//import androidx.compose.material3.IconButton
//import androidx.compose.material3.OutlinedTextField
//import androidx.compose.material3.Scaffold
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.unit.dp
//import androidx.compose.material.icons.*
//import androidx.compose.material.icons.materialIcon
//import androidx.compose.ui.Alignment
//
//
//@Composable
//fun Live2DTalk() {
//    Scaffold(
//        bottomBar = {
//            var text by remember { mutableStateOf("") }
//            Column {
//                Row {
//                    OutlinedTextField(
//                        value = text,
//                        onValueChange = { text = it },
//                        label = { Text("请输入内容") },
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(vertical = 4.dp, horizontal = 16.dp),
//                        shape = RoundedCornerShape(50.dp),
//                    )
//                }
//                Row(
//                    Modifier
//                        .fillMaxWidth()
//                        .padding(10.dp),
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    IconButton(onClick = { /* doSomething() */ }, modifier = Modifier.width(56.dp)) {
//                        Icon(Icons.Default.Attachment, contentDescription = "Localized description")
//                    }
//                    IconButton(onClick = { /* doSomething() */ }, modifier = Modifier.width(56.dp)) {
//                        Icon(Icons.Default.Image, contentDescription = "Localized description")
//                    }
//                    IconButton(onClick = { /* doSomething() */ }, modifier = Modifier.width(56.dp)) {
//                        Icon(Icons.Default.PhotoCamera, contentDescription = "Localized description")
//                    }
//                    Spacer(Modifier.weight(1f))
//                    IconButton(onClick = { /* doSomething() */ }, modifier = Modifier.width(56.dp)) {
//                        Icon(
//                            Icons.Default.Mic,
//                            contentDescription = "Localized description"
//                        )
//                    }
//                    IconButton(onClick = { /* doSomething() */ }, modifier = Modifier.width(56.dp)) {
//                        Icon(
//                            Icons.AutoMirrored.Filled.Send,
//                            contentDescription = "Localized description"
//                        )
//                    }
//                    Spacer(Modifier.width(6.dp))
//                }
//                Spacer(Modifier.height(12.dp))
//            }
//        },
//    ) { innerPadding ->
//        Text(
//            modifier = Modifier.padding(innerPadding),
//            text = "Example of a scaffold with a bottom app bar."
//        )
//    }
//}