<?php
$servername = "localhost";
$username = "id1051297_centaliu";
$password = $_GET["dbpwd"];
$dbname = "id1051297_dbappdata";

// Create connection
$conn = new mysqli($servername, $username, $password, $dbname);
// Check connection
if ($conn->connect_error) {
    die("Connection failed: " . $conn->connect_error);
} 
//try to insert if not exist
$sqlstr = " insert into tbluser (facebookID, isActive, CDateTime, lastLogin, Reserved1) select * from (select '".$_GET["fbid"]."', 1, CURRENT_TIMESTAMP as CDateTime, CURRENT_TIMESTAMP as lastLogin, '') AS tmp where not exists ( select facebookID FROM tbluser where facebookID = '".$_GET["fbid"]."') LIMIT 1";
$conn->query($sqlstr);
//it means the facebook id exists already if affected_rows is zero
if ($conn->affected_rows == 0) {
  $sqlstr = "update tbluser set lastLogin = CURRENT_TIMESTAMP where facebookID = '".$_GET["fbid"]."'";
  $conn->query($sqlstr);
}
//query data of this facebookid
$sqlstr = "select * from tbluser where facebookID = '".$_GET["fbid"]."'";
$result = $conn->query($sqlstr);
$myid = 0;
if ($result->num_rows > 0) {
    // output data of each row
    while($row = $result->fetch_assoc()) {
        $myid = $row["id"];
        echo $row["id"].",".$row["isActive"].",".$row["lastLogin"];
    }
} else {
    echo "0";
}
$conn->close();
?>