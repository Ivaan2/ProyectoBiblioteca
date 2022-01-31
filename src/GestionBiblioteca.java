import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Scanner;

public class GestionBiblioteca {
    private static final String bd = "jdbc:mysql://localhost:3306/biblioteca";
    private static final String user = "root";
    private static final String pwd = "";
    private static Connection con;
    private static final Scanner sc = new Scanner(System.in);
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
        }while(opc != 8);
    }

    private static void tratarMenu(int o, Connection con) {
        switch (o) {
            case 1 -> //Alta socio
                    altaSocio(con);
            case 2 -> //Alta libro
                    altaLibro(con);
            case 3 -> //Busqueda por nombre
                    busquedaPorNombre(con);
            case 4 -> //Realizar prestamo
                    realizarPrestamo(con);
            case 5 -> //Finalizar un prestamo
                    finalizaPrestamo(con);
            case 6 -> //Genera un informe
                    generaInformes(con);
            case 7 -> //Pasar a historico
                    pasarHistorico(con);
        }
    }

    private static void pasarHistorico(Connection con) {
        String titulo = solicitarCadena("Introduzca el título del libro: ");
        convertirTituloAIsbn(con, titulo);
    }

    private static void convertirTituloAIsbn(Connection con, String titulo) {
        PreparedStatement pst;
        PreparedStatement pstLibro = null;
        PreparedStatement pstCopia = null;
        PreparedStatement pstPrestamo = null;
        ResultSet isbn;
        try {
            //Confirmamos que el libro existe
            pst = con.prepareStatement("SELECT isbn FROM libro WHERE titulo = ?;");
            pst.setString(1, titulo);
            isbn = pst.executeQuery();
            if(isbn.next()){
                String sql = "SET FOREIGN_KEY_CHECKS=0;";
                Statement st = con.createStatement();
                if(st.execute(sql)){
                    System.out.println("Se cambia el set.");
                }
                //Transportar datos a hist
                con.setAutoCommit(false);
                pstLibro = tratarLibros(con, isbn.getString(1));
                pstCopia = tratarCopias(con, isbn.getString(1));
                pstPrestamo = tratarPrestamo(con, isbn.getString(1));


                pstPrestamo.executeBatch();
                pstCopia.executeBatch();
                pstLibro.executeBatch();

                con.commit();
            }else{
                System.out.println("No existen libros con ese título.");
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
        }finally {
            if(con != null){
                try {
                    con.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static PreparedStatement tratarPrestamo(Connection con, String isbn) {
        String selectPrestamo = "SELECT idprestamo, idsocio, codcopia, fprestamo, fdevolucion FROM prestamo WHERE codcopia LIKE ?;";
        String insertPrestamoH = "INSERT INTO prestamo_hist (idprestamo_hist, idsocio, codcopia_hist, fprestamo_hist, fdevolucion_hist) values (?, ?, ?, ?, ?);";
        String deletePrestamo = "DELETE FROM prestamo WHERE codcopia LIKE ?";
        PreparedStatement st, pstDelete;
        PreparedStatement pstPrestamo = null;

        try {
            st = con.prepareStatement(selectPrestamo);
            st.setString(1, isbn + "%");
            ResultSet rsPrestamo = st.executeQuery();
            while(rsPrestamo.next()){
                pstPrestamo = con.prepareStatement(insertPrestamoH);
                pstPrestamo.setInt(1, rsPrestamo.getInt(1));
                pstPrestamo.setInt(2, rsPrestamo.getInt(2));
                pstPrestamo.setString(3, rsPrestamo.getString(3));
                pstPrestamo.setDate(4, rsPrestamo.getDate(4));
                pstPrestamo.setDate(5, rsPrestamo.getDate(5));
                pstPrestamo.addBatch();
            }
            pstDelete = con.prepareStatement(deletePrestamo);
            pstDelete.setString(1, isbn + "%");
            if(pstDelete.executeUpdate() != 0){
                System.out.println("Datos borrados de la tabla préstamo correctamente.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return pstPrestamo;
    }

    private static PreparedStatement tratarLibros(Connection con, String isbn) {
        String selectLibros = "SELECT isbn, titulo FROM libro WHERE isbn = ?;";
        String insertLibroH = "INSERT INTO libro (isbn, titulo) values (?, ?);";
        String deleteLibro = "DELETE FROM libro WHERE isbn = ?;";
        PreparedStatement st, pstDelete;
        PreparedStatement pstLibro = null;

        try {
            st = con.prepareStatement(selectLibros);
            st.setString(1, isbn);
            ResultSet rsLibro = st.executeQuery();
            while (rsLibro.next()) {
                pstLibro = con.prepareStatement(insertLibroH);
                pstLibro.setString(1, rsLibro.getString(1));
                pstLibro.setString(2, rsLibro.getString(2));
                pstLibro.addBatch();
            }
            pstDelete = con.prepareStatement(deleteLibro);
            pstDelete.setString(1, isbn);
            if(pstDelete.executeUpdate() != 0){
                System.out.println("Datos borrados de libro con éxito.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return pstLibro;
    }

    private static PreparedStatement tratarCopias(Connection con, String isbn) {
        String selectCopias = "SELECT codcopia, isbn FROM copia WHERE isbn = ?;";
        String insertCopiasH = "INSERT INTO copia_hist (codcopia_hist, isbn_hist) VALUES (?, ?);";
        String deletePrestamo = "DELETE FROM copia WHERE isbn = ?;";
        ResultSet copias;
        PreparedStatement st, pstDeleteCopia;
        PreparedStatement pstCopiasH = null;

        try {
            st = con.prepareStatement(selectCopias);
            st.setString(1, isbn);
            copias = st.executeQuery();
            while(copias.next()){
                pstCopiasH = con.prepareStatement(insertCopiasH);
                pstCopiasH.setString(1, copias.getString(1));
                pstCopiasH.setString(2, copias.getString(2));
                pstCopiasH.addBatch();
            }
            pstDeleteCopia = con.prepareStatement(deletePrestamo);
            pstDeleteCopia.setString(1, isbn + "%");
            if(pstDeleteCopia.executeUpdate() != 0){
                System.out.println("Se han borrado los registros de los prestamos con éxito.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return pstCopiasH;
    }

    private static void generaInformes(Connection con) {
        mostrarLibroMasLeido(con);
        usuarioMasLector(con);
        usuariosConPrestamos(con);
        vecesLibrosPrestados(con);
    }

    private static void vecesLibrosPrestados(Connection con) {
        String sqlIsbn = "select isbn, titulo from libro;";
        Statement stSql;
        ResultSet isbn;
        String numeroISBN;
        String titulo;
        boolean hayPrestamos = false;
        try {
            stSql = con.createStatement();
            isbn = stSql.executeQuery(sqlIsbn);
            while(isbn.next()){
                hayPrestamos = true;
                numeroISBN = isbn.getString(1);
                titulo = isbn.getString(2);
                mostrarRegistro(con, numeroISBN, titulo);
            }
            if(!hayPrestamos){
                System.out.println("No hay préstamos en nuestra base de datos.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void mostrarRegistro(Connection con, String numeroISBN, String titulo) {
        String sql = "select count(*) from prestamo where codcopia like ?;";
        PreparedStatement pst;
        ResultSet registros;
        boolean existe = false;
        try {
            pst = con.prepareStatement(sql);
            pst.setString(1, numeroISBN + "%");
            registros = pst.executeQuery();
            if(registros.next()){
                existe = true;
                if(registros.getInt(1) == 1){
                    System.out.println("Isbn: " + numeroISBN + " Titulo: " + titulo + " 1 vez prestado.");
                }else{
                    System.out.println("Isbn: " + numeroISBN + " Titulo: " + titulo + " " + registros.getInt(1) + " veces prestado.");
                }
            }
            if(!existe){
                System.out.println("No existen consultas.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void usuariosConPrestamos(Connection con) {
        String sql = "select s.dni, s.nombre from socio s join prestamo p on s.idSocio = p.idsocio where p.fdevolucion is null;";
        Statement st;
        ResultSet rs;
        StringBuilder mensaje = new StringBuilder("Personas con préstamos pendientes.");
        try {
            st = con.createStatement();
            rs = st.executeQuery(sql);
            while (rs.next()){
                mensaje.append("\nDNI: ").append(rs.getString(1)).append(" Nombre: ").append(rs.getString(2)).append(".");
            }
            if(mensaje.toString().equals("Personas con préstamos pendientes.")){
                System.out.println("No hay usuarios con préstamos actualmente.");
            }else{
                System.out.println(mensaje.toString());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void usuarioMasLector(Connection con) {
        String selectUsuarioMasLector = "select p.idsocio\n" +
                "from\n" +
                "prestamo p \n" +
                "group by\n" +
                "p.idsocio having count(p.idsocio) =\n" +
                "(select\n" +
                "max(i.total) from\n" +
                "(select count(p2.idsocio) as total\n" +
                "from prestamo p2 group by p2.idsocio) i);";
        String idAnombre = "SELECT nombre FROM socio WHERE idSocio = ?";
        PreparedStatement pstUser;
        Statement pstUsuarioLector;
        ResultSet idUser;
        try {
            pstUsuarioLector = con.createStatement();
            idUser = pstUsuarioLector.executeQuery(selectUsuarioMasLector);

            if(idUser.next()){
                pstUser = con.prepareStatement(idAnombre);
                pstUser.setInt(1, idUser.getInt(1));
                idUser = pstUser.executeQuery();
                if(idUser.next()){
                    System.out.println("El usuario más lector es: " + idUser.getString(1));
                }else{
                    System.out.println("Error en select sql.");
                }
            }else{
                System.out.println("No hay lectores registrados en la biblioteca.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void mostrarLibroMasLeido(Connection con) {
        String sql = "select codcopia from prestamo where fdevolucion is not null ;";
        ArrayList<String> libros = new ArrayList<String>();
        Statement st;
        ResultSet rs;
        boolean existe = false;
        String informacion;
        String libroMasLeido = null;

        try {
            st = con.createStatement();
            rs = st.executeQuery(sql);
            while( rs.next() ){
                existe = true;
                informacion = rs.getString(1).split("_")[0];
                libros.add(informacion);
            }
            if(!existe){
                System.out.println("No se han leído libros aún");
            }
            else {
                ArrayList<String> auxiliar = new ArrayList<String>(libros);
                int contador, max = 0;
                for (String l : libros) {
                    contador = 0;
                    for (String a : auxiliar) {
                        if (l.equals(a)) {
                            contador++;
                        }
                        if (contador > max) { //Se actualiza el libro que mas veces ha sido leído y almaceno el isbn
                            max = contador;
                            libroMasLeido = a;
                        }
                    }
                }
                String sqlLibroMasLeido = "SELECT titulo from libro WHERE isbn = ?";
                PreparedStatement pstLibro = con.prepareStatement(sqlLibroMasLeido);
                pstLibro.setString(1, libroMasLeido);
                ResultSet rsLibro = pstLibro.executeQuery();
                if (rsLibro.next()) {
                    System.out.println("El libro más leído es: " + rsLibro.getString(1));
                } else {
                    System.out.println("Error en select de isbn a titulo del libro.");
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
        String isbn = solicitarCadena("Introduzca el isbn del libro: ");
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
                                System.out.println("Préstamo finalizado correctamente.");
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
        int numCopias;
        String isbnLibro;

        if(comprobarDni(dniSocio, con)){
            isbnLibro = solicitarCadena("Introduzca el isbn del libro: ");
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

    private static void comprobarCopias(String isbnLibro, Connection con, String dniSocio) {
        //Bucle
        //  select de las copias
        //  con el next ir recorriendo y comprobando si el libro está prestado
        //Lo tienes Iván
        String SelectCopias = "SELECT codcopia FROM copia WHERE isbn = ?;";
        //TODO verificar que esa copia existe para ver si sigue en prestamo
        ResultSet rs;
        String codcopia = null;
        try {
            PreparedStatement pstCopias = con.prepareStatement(SelectCopias);
            pstCopias.setString(1, isbnLibro);
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
            if(pst.executeUpdate() != 0){
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
            ResultSet rs = pst.executeQuery();
            if(rs.next()){
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

    private static int copiasDisponibles(Connection con, String isbnLibro) {
        String sqlCopiaLibro = "SELECT codcopia FROM copia WHERE isbn = ?";
        PreparedStatement pst = null;
        ResultSet rsCopias;
        int numeroCopias = 0;

        try {
            pst = con.prepareStatement(sqlCopiaLibro);
            pst.setString(1, isbnLibro);
            rsCopias = pst.executeQuery();
            while(rsCopias.next()){
                numeroCopias++;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return numeroCopias;
    }

    private static boolean comprobarISBN(String isbnLibro, Connection con) {
        String sql = "SELECT * FROM libro WHERE isbn = ?;";
        ResultSet rs;
        boolean existe = false;
        try {
            PreparedStatement pst = con.prepareStatement(sql);
            pst.setString(1, isbnLibro);
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
            PreparedStatement pst = con.prepareStatement("SELECT titulo, isbn FROM libro WHERE titulo LIKE ?;");
            pst.setString(1, "%" + pista + "%");
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
            PreparedStatement pst = con.prepareStatement("SELECT nombre, dni FROM socio WHERE nombre LIKE ?;");
            pst.setString(1, "%" + pista + "%");
            rs = pst.executeQuery();
            while(rs.next()){
                existe = true;
                System.out.println("Dni: " + rs.getString(2) + ", nombre: " + rs.getString(1));
            }
            if(!existe)
                System.out.println("No se han encontrado resultados con tales características.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static int mostrarOpciones() {
        System.out.println("Elija la opción que desee:" +
                "\n1- Buscar socio" +
                "\n2- Buscar libro");
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
        String isbn = solicitarCadena("Introduce el isbn del nuevo libro: ");
        String titulo = solicitarCadena("Introduce el titulo del nuevo libro: ");

        try {
            con.setAutoCommit(false);

            pstCopias = con.prepareStatement(INSERT_COPIA);
            pstLibro = con.prepareStatement(INSERT_LIBRO);
            pstExisteLibro = con.prepareStatement(selectLibroISBN);
            pstExisteLibro.setString(1, isbn);
            ResultSet rs = pstExisteLibro.executeQuery();
            int numCopias;

            if(!rs.next()){  //Si no existe se puede insertar
                do{
                    numCopias = solicitarEntero("Introduce el numero de copias para el nuevo libro: ");
                }while(numCopias < 0);

                pstLibro.setString(1, isbn);
                pstLibro.setString(2, titulo);
                pstLibro.addBatch();

                for (int i = 1; i <= numCopias; i++){
                    pstCopias.setString(1, (isbn + "_" + i));
                    pstCopias.setString(2, isbn);
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
        String dni;
        do{
            dni = solicitarCadena("Introduce un dni para dar de alta un nuevo socio: ");
        }while (!comprobarLongitudDni(dni));

        try {
            //Comprobamos que el socio no exista para poder introducirlo
            pstSocioExiste = con.prepareStatement(selectSocio);
            pstSocioExiste.setString(1, dni);
            ResultSet rs = pstSocioExiste.executeQuery();
            if(!rs.next()){
                //Si no existe un socio con ese dni lo insertamos
                pst = con.prepareStatement(INSERT_SOCIO);
                pst.setString(1, nombre);
                pst.setString(2, dni);
                if(pst.executeUpdate() != 0){
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

    private static boolean comprobarLongitudDni(String dni) {
        boolean correcto = true;
        if(dni.length() != 9){
            System.out.println("Introduzca un dni de 9 caracteres.");
            correcto = false;
        }
        return correcto;
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
        int n;
        do{
            System.out.println("""
                ** BIBLIOTECA **
                1- Alta socio
                2- ALta libro
                3- Búsqueda por nombre
                4- Realizar un préstamo
                5- Finalizar un préstamo
                6- Genera un informe
                7- Pasar a histórico
                8- Salir""");
            System.out.println("\nIntroduzca una opción: ");
            n = Integer.parseInt(sc.nextLine());
        }while(n < 1 || n > 8);
        return n;
    }
}