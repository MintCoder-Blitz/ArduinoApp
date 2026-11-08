import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "riego_app.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase?) {
        // Tabla con campos optimizados solo para nombre y contraseña
        val crearTabla = "CREATE TABLE usuarios (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT, contrasena TEXT)"
        db?.execSQL(crearTabla)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS usuarios")
        onCreate(db)
    }

    // 1. Guardar el nombre y la contraseña
    fun insertarUsuario(nombre: String, contrasena: String): Boolean {
        val db = this.writableDatabase
        val valores = ContentValues().apply {
            put("nombre", nombre)
            put("contrasena", contrasena)
        }
        val resultado = db.insert("usuarios", null, valores)
        return resultado != -1L // Retorna true si se guardó bien
    }

    // 2. Verificar si existen para el inicio de sesión
    fun verificarUsuario(nombre: String, contrasena: String): Boolean {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM usuarios WHERE nombre = ? AND contrasena = ?", arrayOf(nombre, contrasena))
        val existe = cursor.count > 0
        cursor.close()
        return existe
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
        return writableDatabase.delete(
            "usuarios",
            "nombre = ? AND contrasena = ?",
            arrayOf(nombre, contrasena)
        ) > 0
    }
}