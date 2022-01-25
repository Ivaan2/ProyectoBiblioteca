import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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
                break;

            case 5: //Finalizar un prestamo
                break;

            case 6: //Genera un informe
                break;

            case 7: //Pasar a historico
                break;
        }
    }

    private static void busquedaPorNombre(Connection con) {
        //Elegir entre socio o libro
        //Modularizarlo en dos métodos
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
