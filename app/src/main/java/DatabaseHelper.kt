import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "riego_app.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(
            "CREATE TABLE usuarios (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "nombre TEXT NOT NULL UNIQUE, " +
                "contrasena TEXT NOT NULL, " +
                "rol TEXT NOT NULL CHECK(rol IN ('admin', 'usuario')))"
        )
        db?.execSQL(
            "INSERT OR IGNORE INTO usuarios(nombre, contrasena, rol) " +
                "VALUES('admin', 'admin123', 'admin')"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db?.execSQL("ALTER TABLE usuarios ADD COLUMN rol TEXT")
            db?.execSQL("UPDATE usuarios SET rol = 'usuario' WHERE rol IS NULL")
            db?.execSQL(
                "INSERT OR IGNORE INTO usuarios(nombre, contrasena, rol) " +
                    "VALUES('admin', 'admin123', 'admin')"
            )
        }
    }

    fun insertarUsuario(nombre: String, contrasena: String, rol: String = "usuario"): Boolean {
        if (rol != "admin" && rol != "usuario") return false
        val db = this.writableDatabase
        val valores = ContentValues().apply {
            put("nombre", nombre)
            put("contrasena", contrasena)
            put("rol", rol)
        }
        return db.insert("usuarios", null, valores) != -1L
    }

    fun verificarUsuario(nombre: String, contrasena: String): String? {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM usuarios WHERE nombre = ? AND contrasena = ?", arrayOf(nombre, contrasena))
        val rol = if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow("rol")) else null
        cursor.close()
        return rol
    }

    fun obtenerRol(nombre: String): String? {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT rol FROM usuarios WHERE nombre = ?", arrayOf(nombre))
        val rol = if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow("rol")) else null
        cursor.close()
        return rol
    }

    fun actualizarContrasena(nombre: String, nuevaContrasena: String): Boolean {
        val valores = ContentValues().apply {
            put("contrasena", nuevaContrasena)
        }
        return writableDatabase.update(
            "usuarios",
            valores,
            "nombre = ?",
            arrayOf(nombre)
        ) > 0
    }

    fun eliminarUsuario(nombre: String, contrasena: String): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val countCursor = db.rawQuery("SELECT COUNT(*) FROM usuarios", null)
            countCursor.moveToFirst()
            val total = countCursor.getInt(0)
            countCursor.close()

            if (total <= 1) {
                false
            } else {
                val eliminados = db.delete(
                    "usuarios",
                    "nombre = ? AND contrasena = ?",
                    arrayOf(nombre, contrasena)
                )
                if (eliminados > 0) db.setTransactionSuccessful()
                eliminados > 0
            }
        } finally {
            db.endTransaction()
        }
    }

    fun contarUsuarios(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM usuarios", null)
        val total = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        return total
    }
}