# Propagate manual_ok_all_releases from one release to another

use strict;
use DBI;
use Getopt::Long;
use IO::File;
use File::Basename;

my ($host, $port, $dbname, $user, $pass, $dbname, $old_release, $new_release, $quiet, $new_dbname);

GetOptions('user=s'            => \$user,
	   'pass=s'            => \$pass,
	   'host=s'            => \$host,
	   'port=i'            => \$port,
	   'dbname=s'          => \$dbname,
           'old_release=i'     => \$old_release,
	   'new_release=i'     => \$new_release,
	   'quiet'             => \$quiet,
	   'new_dbname=s'      => \$new_dbname,
	   'help'              => sub { usage(); exit(0); });

# TODO - set defaults to ens-staging

$user    = $user || "ensadmin";
$pass    = $pass || "ensembl";
$host    = $host || "ens-staging";
$port    = $port ||  "3306";
$dbname  = $dbname || "healthchecks";

if (!($old_release && $new_release)) {
  print "Must specify -old_release and -new_release\n";
  exit(1);
}

# connect to healthcheck database
my $dbi = DBI->connect("DBI:mysql:host=$host:port=$port;database=$dbname", $user, $pass,
				  {'RaiseError' => 1}) || die "Can't connect to healthcheck database\n";

# cache database name mappings
my $old_to_new_db_name;
my $session_id;
#if we are propagating all the databases within the release, get all databases from staging 
if (!$new_dbname){
    $old_to_new_db_name = create_db_name_cache($dbi, $old_release, $new_release);
    # create new session for new release
    $session_id = create_session($dbi, $old_release, $new_release);
}
else{
    #we are propagating a single database
    $old_to_new_db_name = create_db_name_cache($dbi,$old_release,$new_release,$new_dbname);
    #we will use the latest session_id (if it has already been created)  
    $session_id = get_latest_session_id($dbi,$new_release);
    if ($session_id < 0){
	#the session_id is not correct, probably not run propagate before;
	print STDERR "There is no session_id for release $new_release in the database\n. Have you propagated all the databases before?\n";
	exit(1);
    }
}

# propagate! propagate!
propagate($dbi, $old_release, $new_release, $session_id, $old_to_new_db_name);

# --------------------------------------------------------------------------------

sub create_db_name_cache {

  my ($dbi, $old_release, $new_release, $new_dbname) = @_;

  $new_dbname ||= undef;

  my %cache;

  # get list of databases for old session
  my @old_dbs;
  my $sth = $dbi->prepare("SELECT distinct(database_name) from report WHERE database_name LIKE ?");
  if ($new_dbname){
      #only propagate for new_dbname
      $new_dbname =~ /([a-z]+_[a-z]+_[a-z]+)_\d+/;
      $sth->execute("%$1_$old_release%");
  }
  else{
      #propagate for all databases in old_release
      $sth->execute("%_${old_release}_%");
  }
  foreach my $row (@{$sth->fetchall_arrayref()}) {
    push @old_dbs, $row->[0];
  }

  # get list of new databases
  my @new_dbs;
  $sth = $dbi->prepare("SHOW DATABASES LIKE ?");
  if ($new_dbname){
      #get a single database
      $sth->execute("%$new_dbname%");
  }
  else{
      #get all databases in this new_release
      $sth->execute("%_${new_release}_%"); # note this will exclude master_schema_48 etc
  }
  foreach my $row (@{$sth->fetchall_arrayref()}) {
    push @new_dbs, $row->[0];
  }

  # create mapping
  my %old_to_new = {};
  my $missing;
  foreach my $old_db (@old_dbs) {
    my $new_db = find_match($old_db, @new_dbs);
    if ($new_db) {
      #print "$old_db -> $new_db\n" unless ($quiet);
      $cache{$old_db} = $new_db;
    } else {
      print STDERR "Can't find equivalent new database for $old_db\n";
  #    $missing = 1;
    }

  }
  #you still want to propagate other databases even if some of the new ones are not ready yet 
  #if ($missing) {
   # print STDERR "Can't find mappings for all old databases, exiting\n";
   # exit(1);
  #}

  return \%cache;

}

# --------------------------------------------------------------------------------

sub find_match {

  my ($old_db, @new_dbs) = @_;

  my $match;

  foreach my $new_db (@new_dbs) {

    $match = $new_db if (compare_dbs($old_db, $new_db));

  }

  return $match;
}

# --------------------------------------------------------------------------------

sub compare_dbs {

  my ($old, $new) = @_;

  my @o = split(/_/, $old);

  my @n = split(/_/, $new);

  # if first 3 bits are the same, we have a match
  return ($o[0] eq $n[0] && $o[1] eq $n[1] && $o[2] eq $n[2]);

}

# --------------------------------------------------------------------------------

sub create_session {

  my ($dbi, $old_release, $new_release) = @_;

  my $sth = $dbi->prepare("INSERT INTO session (db_release,config) VALUES (?,?)");
  $sth->execute($new_release, "Propagation of entries from release $old_release to release $new_release") || die "Error creating new session\n";

  my $session_id = $sth->{'mysql_insertid'};

  print "Created session ID $session_id for propagation\n" unless $quiet;

  return $session_id;

}

# --------------------------------------------------------------------------------

sub propagate {

  my ($dbi, $old_release, $new_release, $propagation_session_id, $old_to_new_db_name) = @_;

  my @types = qw(manual_ok_all_releases manual_ok_this_assembly);

  my $select_sth = $dbi->prepare("SELECT r.first_session_id, r.species, r.database_type, r.timestamp, r.testcase, r.result, r.text, a.person, a.action,a.comment,a.created_at, a.modified_at, a.created_by, a.modified_by FROM report r, annotation a WHERE a.report_id=r.report_id AND r.database_name=? AND a.action=?");

  my $insert_report_sth = $dbi->prepare("INSERT INTO report (first_session_id, last_session_id, database_name, database_type, species, timestamp, testcase, result, text) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");

  my $insert_annotation_sth = $dbi->prepare("INSERT INTO annotation (report_id, person, action, comment, created_at, modified_at, created_by, modified_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");

  my %counts;

  foreach my $type (@types) {

    print "Propagating $type\n" unless ($quiet);
    $counts{$type} = 0;

    # find all manual_ok_all_releases etc for each old database
    foreach my $old_database (keys %$old_to_new_db_name) {

      my $new_database = $old_to_new_db_name->{$old_database};
      
      my $count = 0;

      $select_sth->execute($old_database, $type);
      foreach my $row (@{$select_sth->fetchall_arrayref()}) {

	my ($first_session_id, $species, $database_type, $timestamp, $testcase, $result, $text, $person, $action,$comment,$created_at, $modified_at, $created_by, $modified_by) = @$row;
	
	#type eq manual_ok_this_assembly:
	#will only need to propagate if it is the same assembly
	if ($type eq 'manual_ok_this_assembly'){
	    #compare the assembly component from the database name
	    my $is_new_assembly = new_assembly($old_database,$new_database);
	    #if it is a new assembly, do not propagate this HC
	    next if $is_new_assembly;
	}
	# create new report
	$insert_report_sth->execute($first_session_id, $propagation_session_id, $new_database, $database_type, $species, $timestamp, $testcase, $result, $text) || die "Error inserting report";
	my $report_id = $insert_report_sth->{'mysql_insertid'};
	# create new annotation
	$insert_annotation_sth->execute($report_id, $person, $action, $comment, $created_at, $modified_at, $created_by, $modified_by) || die "Error inserting annotation";
	$count++;
	$counts{$type}++;

      }

      print "\t$old_database\t$count\n" if (!$quiet && $count > 0);

    }
    print "Propagated " . $counts{$type} . " $type reports\n" unless ($quiet);

  } # foreach type


}

# --------------------------------------------------------------------------------

#this method will return true if it is a new specie assembly, false otherwise
#will check the number in the name. e.g. homo_sapiens_core_53_(36)o
sub new_assembly{
    my $old_database = shift;
    my $new_database = shift;

    my ($old_db_assembly) = $old_database =~ /_(\d+)[a-z]{0,1}$/;
    my ($new_db_assembly) = $new_database =~ /_(\d+)[a-z]{0,1}$/;

    return ($old_db_assembly != $new_db_assembly);
}

# --------------------------------------------------------------------------------

#method that will return the latest session_id in the database for the new_release
sub get_latest_session_id{
    my ($dbi,$new_release) = @_;
    #get latest session_id for the new release
    my $sth_session_id = $dbi->prepare("SELECT max(session_id) FROM session WHERE db_release = ?");
    $sth_session_id->execute($new_release);
    my $latest_session_id = $sth_session_id->fetch();
    if ($latest_session_id->[0]){
	return $latest_session_id->[0];
    }
    else{
	return -1;
    }
}
