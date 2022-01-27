import javax.xml.transform.Result;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Scanner;

public class GestionBiblioteca {
    private static final String bd = "jdbc:mysql://localhost:3306/biblioteca";
    private static final String user = "root";
    private static final String pwd = "";
    private static Connection con;
    private static Scanner sc = new Scanner(System.in);
    private static final String INSERT_SOCIO = "INSERT INTO socio (nombre, dni) values (?, ?)";
    private static final String INSERT_LIBRO = "INSERT INTO libro (isbn, titulo) values (?, ?)";
    private static final String INSERT_COPIA = "INSERT INTO copia (codcopia, isbn) values (?, ?)";
    private static final String INSERT_PRESTAMO = "INSERT INTO prestamo (idsocio, codcopia, fprestamo, fdevolucion) values (?, ?, ?, ?)";
    private static final String FINALIZAR_PRESTAMO = "UPDATE prestamo set fdevolucion = ? WHERE idsocio = ? and codcopia LIKE ?;";

    public static void main(String[] args) {
        int opc = 0;
        try {
            con = DriverManager.getConnection(bd, user, pwd);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        do {
            opc = mostrarMenu();
            tratarMenu(opc, con);
        }while(opc != 1 && opc != 2 && opc != 3 && opc != 4 && opc != 5 && opc != 6 && opc != 7);
    }

    private static void tratarMenu(int o, Connection con) {
        switch(o){
            case 1: //Alta socio
                altaSocio(con);
                break;

            case 2: //Alta libro
                altaLibro(con);
                break;

            case 3: //Busqueda por nombre
                busquedaPorNombre(con);
                break;

            case 4: //Realizar prestamo
                realizarPrestamo(con);
                break;

            case 5: //Finalizar un prestamo
                finalizaPrestamo(con);
                break;

            case 6: //Genera un informe
                generaInformes(con);
                break;

            case 7: //Pasar a historico
                break;
        }
    }

    private static void generaInformes(Connection con) {
        mostrarLibroMasLeido(con);
        //usuarioMasLector(con);
        //mostrarUsuarios con prestamos actuales
        //listado de libros y prestamos
    }

    private static void mostrarLibroMasLeido(Connection con) {
        String sql = "select codcopia from prestamo where fdevolucion is not null ;";
        ArrayList<String> libros = new ArrayList<String>();
        HashMap<String, Integer> libro = new HashMap<String, Integer>();
        Statement st;
        ResultSet rs;
        boolean existe = false;
        String informacion;

        try {
            st = con.createStatement();
            rs = st.executeQuery(sql);
            while( rs.next() ){
                existe = true;
                informacion = rs.getString(1).split("_")[0];
                if(!libro.containsKey(informacion)){
                    libro.put(informacion, 1);
                }else{
                    //libro.set
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void finalizaPrestamo(Connection con) {
        //Pedir dni
        //Pedir ISBN
        String dni = solicitarCadena("Introduzca el dni: ");
        int isbn = solicitarEntero("Introduzca el isbn del ");
        Date date = new Date();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String formattedDate = simpleDateFormat.format(date);
        java.sql.Date dateSql = java.sql.Date.valueOf(formattedDate);
        PreparedStatement pst;
        ResultSet rsIdSocio, rsAux;
        PreparedStatement pstDniASocio;

        try {
            pstDniASocio = con.prepareStatement("SELECT idSocio FROM socio WHERE dni = ?;");
            pstDniASocio.setString(1, dni);
            rsIdSocio = pstDniASocio.executeQuery();

            if(rsIdSocio.next()){
                //Si el libro y el dni corresponden al mismo prestamo, se finaliza
                PreparedStatement pstAux = con.prepareStatement("SELECT * FROM prestamo WHERE idsocio = ? and codcopia LIKE ?;");
                pstAux.setInt(1, rsIdSocio.getInt(1));
                pstAux.setString(2, isbn + "%");
                rsAux = pstAux.executeQuery();

                //Sin tener en cuenta que una misma persona vaya a recoger dos veces el mismo libro,
                //uso un if ya que un while solo tendrçia sentido si hubiese más de 1 préstamo con la
                //misma persona y el codcopia correspondiente al mismo libro (caso que descarto)
                if(rsAux.next()){
                    System.out.println("Datos insertados correctamente. Se está tramitando el fin del préstamo.");
                    if(rsAux.getDate("fdevolucion") != null){
                        System.out.println("Este libro ya ha sido devuelto.");
                        }else {
                            pst = con.prepareStatement(FINALIZAR_PRESTAMO);
                            pst.setDate(1, dateSql);
                            pst.setInt(2, rsIdSocio.getInt(1));
                            pst.setString(3, isbn + "%");
                            if(pst.executeUpdate() != 0){
                                System.out.println("Préstamo realizado correctamente.");
                            }else{
                                System.out.println("Lo sentimos, no se ha podido realizar el fin del préstamo.");
                            }
                    }
                }else{
                    System.out.println("Los datos insertados no coinciden.");
                }
            }else{
                System.out.println("Los sentimos, el dni que has insertado no se encuentra en nuestra base de datos.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void realizarPrestamo(Connection con) {
        String dniSocio = solicitarCadena("Introduzca su dni: ");
        int isbnLibro, numCopias;

        if(comprobarDni(dniSocio, con)){
            isbnLibro = solicitarEntero("Introduzca el isbn del libro: ");
            if(comprobarISBN(isbnLibro, con)){
                //DNI e ISBN correctos
                //Comprobar que quedan copias del libro disponibles
                numCopias = copiasDisponibles(con, isbnLibro);
                //Comprobar que el socio no tenga prestamos actuales
                if(socioSinPrestamos(con, dniSocio)){
                    //No tiene prestamos actualmente
                    comprobarCopias(isbnLibro, con, dniSocio);
                }
                else{
                    System.out.println("El socio tiene ya un préstamo.");
                }
            }else{
                System.out.println("El isbn " + isbnLibro + " no se encuentra en nuestra base de datos.");
            }
        }else{
            System.out.println("El dni identificativo " + dniSocio + " del socio no exite en nuestra base de datos.");
        }
    }

    private static void comprobarCopias(int isbnLibro, Connection con, String dniSocio) {
        //Bucle
        //  select de las copias
        //  con el next ir recorriendo y comprobando si el libro está prestado
        //Lo tienes Iván
        String SelectCopias = "SELECT codcopia FROM copia WHERE isbn = ?;";
        ResultSet rs;
        String codcopia = null;
        try {
            PreparedStatement pstCopias = con.prepareStatement(SelectCopias);
            pstCopias.setInt(1, isbnLibro);
            rs = pstCopias.executeQuery();

            boolean adjudicado = false;
            while(rs.next() && !adjudicado){
                codcopia = rs.getString(1);
                if(disponibilidadCopia(codcopia, con)){
                    adjudicado = true;
                }
            }

            if(adjudicado){
                insertarPrestamo(con, dniSocio, codcopia);
                System.out.println(codcopia + " reservado.");
            }else{
                System.out.println("Todas las copias se han prestado ya.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    private static void insertarPrestamo(Connection con, String dniSocio, String codcopia) {
        //INSERT INTO prestamo (idsocio, codcopia, fprestamo, fdevolucion) values (?, ?, ?, ?)
        int idSocio = 0;
        Date date = new Date();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String formattedDate = simpleDateFormat.format(date);
        java.sql.Date dateSql = java.sql.Date.valueOf(formattedDate);
        PreparedStatement pst;
        try {
            pst = con.prepareStatement("SELECT idSocio FROM socio WHERE dni = ?;");
            pst.setString(1, dniSocio);
            ResultSet rs = pst.executeQuery();
            if(rs.next()){
                idSocio = (rs.getInt(1));
            }

            pst = con.prepareStatement(INSERT_PRESTAMO);
            pst.setInt(1, idSocio);
            pst.setString(2, codcopia);
            pst.setDate(3, dateSql);
            pst.setDate(4, null);
            if(pst.execute()){
                System.out.println("Préstamo realizado correctamente.");
            }else{
                System.out.println("Error al prestar el libro.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static boolean disponibilidadCopia(String codcopia, Connection con) {
        boolean disponible = true;
        String sql = "SELECT * FROM  prestamo WHERE codcopia = ? and fdevolucion is null;";

        try {
            PreparedStatement pst = con.prepareStatement(sql);
            pst.setString(1, codcopia);
            if(pst.execute()){
                //Si la consulta devuelve algo, el método execute es True, por lo disponble -> false
                disponible = false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return disponible;
    }

    private static boolean socioSinPrestamos(Connection con, String dniSocio) {
        String sqlSocio = "SELECT * FROM prestamo p JOIN socio s ON s.idSocio = p.idSocio WHERE fdevolucion is null and s.dni = ?;";
        ResultSet rs;
        boolean sinPrestamos = true;
        try {
            PreparedStatement pst = con.prepareStatement(sqlSocio);
            pst.setString(1, dniSocio);
            rs = pst.executeQuery();
            if(rs.next()){
                //False si tiene prestamos vigentes
                sinPrestamos = false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return sinPrestamos;
    }

    private static int copiasDisponibles(Connection con, int isbnLibro) {
        String sqlCopiaLibro = "SELECT codcopia FROM copia WHERE isbn = ?";
        PreparedStatement pst = null;
        ResultSet rsCopias;
        int numeroCopias = 0;

        try {
            pst = con.prepareStatement(sqlCopiaLibro);
            pst.setInt(1, isbnLibro);
            rsCopias = pst.executeQuery();
            while(rsCopias.next()){
                numeroCopias++;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return numeroCopias;
    }

    private static boolean comprobarISBN(int isbnLibro, Connection con) {
        String sql = "SELECT * FROM libro WHERE isbn = ?;";
        ResultSet rs;
        boolean existe = false;
        try {
            PreparedStatement pst = con.prepareStatement(sql);
            pst.setInt(1, isbnLibro);
            rs = pst.executeQuery();
            if(rs.next()){
                existe = true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return existe;
    }

    //Comprimir metodos
    private static boolean comprobarDni(String dniSocio, Connection con) {
        String sql = "SELECT * FROM socio WHERE dni = ?;";
        ResultSet rs;
        boolean existe = false;
        try {
            PreparedStatement pst = con.prepareStatement(sql);
            pst.setString(1, dniSocio);
            rs = pst.executeQuery();
            if(rs.next()){
                existe = true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return existe;
    }

    private static void busquedaPorNombre(Connection con) {
        //Elegir entre socio o libro
        //Modularizarlo en dos métodos
        int opc;
        do{
            opc = mostrarOpciones();
        }while(opc < 1 || opc > 2);
        tratarOpciones(opc, con);
    }

    private static void tratarOpciones(int opc, Connection con) {
        switch (opc){
            case 1:
                buscarSocio(con);
                break;

            case 2:
                buscarLibro(con);
                break;
        }
    }

    private static void buscarLibro(Connection con) {
        String pista = solicitarCadena("Introduzca el título del libro: ");
        ResultSet rs;
        boolean existe = false;
        try {
            PreparedStatement pst = con.prepareStatement("SELECT titulo, isbn FROM libro WHERE titulo LIKE %" + pista + "%;");
            rs = pst.executeQuery();
            while(rs.next()){
                existe = true;
                System.out.println("ISBN: " + rs.getString(2) + ", título: " + rs.getString(1));
            }
            if(!existe)
                System.out.println("No se han encontrado resultados con tales características.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void buscarSocio(Connection con) {
        String pista = solicitarCadena("Introduzca el nombre del socio: ");
        ResultSet rs;
        boolean existe = false;
        try {
            PreparedStatement pst = con.prepareStatement("SELECT nombre, dni FROM socio WHERE nombre LIKE %" + pista + "%;");
            rs = pst.executeQuery();
            while(rs.next()){
                existe = true;
                System.out.println("Dni: " + rs.getString(1) + ", nombre: " + rs.getString(2));
            }
            if(!existe)
                System.out.println("No se han encontrado resultados con tales características.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static int mostrarOpciones() {
        System.out.println("Elija la opción que desee:" +
                "1- Buscar socio" +
                "2- Buscar libro");
        return Integer.parseInt(sc.nextLine());
    }

    private static void altaLibro(Connection con) {
        /*
        Implementa un método altaLibro para dar de alta un libro y sus copias en
        nuestro sistema. Solicitará al usuario título, isbn y número de copias. Controla
        las distintas excepciones que puedan suceder, así como el control transaccional
        para que la información no quede inconsistente.
         */

        String selectLibroISBN = "SELECT * FROM libro WHERE isbn = ?";

        PreparedStatement pstLibro;
        PreparedStatement pstExisteLibro;
        PreparedStatement pstCopias;
        int isbn = solicitarEntero("Introduce el isbn del nuevo libro: ");
        String titulo = solicitarCadena("Introduce el titulo del nuevo libro: ");

        try {
            con.setAutoCommit(false);

            pstCopias = con.prepareStatement(INSERT_COPIA);
            pstLibro = con.prepareStatement(INSERT_LIBRO);
            pstExisteLibro = con.prepareStatement(selectLibroISBN);
            pstExisteLibro.setInt(1, isbn);

            if(!pstExisteLibro.execute()){  //Si no existe se puede insertar
                int numCopias = solicitarEntero("Introduce el numero de copias para el nuevo libro");
                pstLibro.setInt(1, isbn);
                pstLibro.setString(2, titulo);
                pstLibro.addBatch();

                for (int i = 1; i <= numCopias; i++){
                    pstCopias.setString(1, (isbn + "_" + i));
                    pstCopias.setInt(2, isbn);
                    pstCopias.addBatch();
                }

                pstLibro.executeBatch();
                pstCopias.executeBatch();
                con.commit();

                System.out.println("El libro se ha dado de alta con éxito.");
                System.out.println("Las copias han sido insertadas con éxito.");
            }else{
                System.out.println("El isbn introducido ya se encuentra registrado en nuestra base de datos.");
            }
        } catch (SQLException e) {
            errorSQL(e);
            try {
                con.rollback();
                System.out.println("Rollback realizado");
            }catch(SQLException re) {
                errorSQL(e);
                System.out.println("Error realizando Rollback");
            }
        }




    }

    private static void errorSQL(SQLException e) {
        System.err.println(" SQL ERROR mensaje : " + e.getMessage());
        System.err.println(" SQL Estado : " + e.getSQLState());
        System.err.println(" SQL código especifico: " + e.getErrorCode());
    }

    private static void altaSocio(Connection con) {
        /*
        Implementa un método altaSocio para dar de alta un socio en nuestro sistema.
        Solicitará nombre y dni. Controla las distintas excepciones que puedan suceder
         */
        String selectSocio = "SELECT * FROM socio WHERE dni = ?";
        PreparedStatement pst, pstSocioExiste;
        String nombre = solicitarCadena("Introduce un nombre para dar de alta un nuevo socio: ");
        String dni = solicitarCadena("Introduce un dni para dar de alta un nuevo socio: ");

        try {
            //Comprobamos que el socio no exista para poder introducirlo
            pstSocioExiste = con.prepareStatement(selectSocio);
            pstSocioExiste.setString(1, dni);
            if(!pstSocioExiste.execute()){  //Devuelve false si no existe
                //Si no existe un socio con ese dni lo insertamos
                pst = con.prepareStatement(INSERT_SOCIO);
                pst.setString(1, nombre);
                pst.setString(2, dni);
                if(pst.execute()){
                    System.out.println("Socio insertado correctamente");
                }else{
                    System.err.println("Error al insertar el nuevo socio.");
                }
            }else{
                System.out.println("Ya hay un socio con ese dni en nuestra base de datos, asegurese de que los datos son correctos.");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static int solicitarEntero(String s) {
        System.out.println(s);
        return Integer.parseInt(sc.nextLine());
    }

    private static String solicitarCadena(String s) {
        System.out.println(s);
        return sc.nextLine();
    }

    private static int mostrarMenu() {
        System.out.println("""
                ** BIBLIOTECA **
                1- Alta socio
                2- ALta libro
                3- Búsqueda por nombre
                4- Realizar un préstamo
                5- Finalizar un préstamo
                6- Genera un informe
                7- Pasar a histórico""");
        System.out.println("\nIntroduzca una opción: ");
        return Integer.parseInt(sc.nextLine());
    }

}
