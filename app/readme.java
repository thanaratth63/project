// ตัวอย่าง: แสดงข้อความผลลัพธ์ที่ตรวจจับใน AlertDialog
//                val resultText = "ข้อความผลลัพธ์ที่ตรวจจับได้" // นำข้อความผลลัพธ์ที่ต้องการแสดงมาใส่ในตัวแปรนี้
//
//                val builder = AlertDialog.Builder(this@MainActivity)
//                builder.setTitle("ผลลัพธ์ที่ตรวจจับ")
//                builder.setIcon(android.R.drawable.ic_dialog_info)
//
//                // สร้าง TextView ใน AlertDialog และกำหนดข้อความที่จะแสดง
//                val textView = TextView(this@MainActivity)
//                textView.text = resultText
//
//                builder.setView(textView)
//                builder.setPositiveButton("ปิด") { dialog, _ -> dialog.dismiss() }
//
//                val alertDialog = builder.create()
//                alertDialog.show()

//                scores.forEachIndexed { index, fl ->
//                    x = index
//                    x *= 4
//                    if (fl > 0.5) {
//                        // คำนวณค่า label และความน่าจะเป็น (score) ของวัตถุที่ตรวจจับได้
//                        val detectedLabel = labels[classes[index].toInt()]
//                        val score = fl.toString()
//
//                        // เก็บข้อความ label ที่ตรวจจับได้ไว้ในตัวแปร resultLabel
//                        resultLabel += "$detectedLabel: $score\n"
//                    }
//                }
//
//                val builder = AlertDialog.Builder(this@MainActivity)
//                builder.setTitle("ผลลัพธ์ที่ตรวจจับ")
//                builder.setIcon(android.R.drawable.ic_dialog_info)
//
//// สร้าง TextView ใน AlertDialog และกำหนดข้อความที่จะแสดง
//                val textView = TextView(this@MainActivity)
//                textView.text = resultLabel
//
//                builder.setView(textView)
//                builder.setPositiveButton("ปิด") { dialog, _ -> dialog.dismiss() }
//
//                val alertDialog = builder.create()
//                alertDialog.show

