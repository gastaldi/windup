<!DOCTYPE html>
<html lang="en">

  <head>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <title>Windup Rule Providers</title>
    <link href="resources/css/bootstrap.min.css" rel="stylesheet">
    <link href="resources/css/windup.css" rel="stylesheet" media="screen">
    <link href="resources/css/windup.java.css" rel="stylesheet" media="screen">
  </head>
  <body role="document">
    
    <!-- Fixed navbar -->
    <div class="navbar-fixed-top windup-bar" role="navigation">
      <div class="container theme-showcase" role="main">
        <img src="resources/img/windup-logo.png" class="logo"/>
      </div>
    </div>

    <div class="container" role="main">
    <div class="row">
      <div class="page-header page-header-no-border">
        <h1>
        	Rule Provider Executions
		</h1>
         <div class="navbar navbar-default">
          <div class="navbar-header">
          <button type="button" class="navbar-toggle" data-toggle="collapse" data-target=".navbar-responsive-collapse">
            <span class="icon-bar"></span>
            <span class="icon-bar"></span>
            <span class="icon-bar"></span>
          </button>
        </div>
        <div class="navbar-collapse collapse navbar-responsive-collapse">
          <ul class="nav navbar-nav">
            <li><a href="../index.html">&lt;- All Applications</a></li>
          </ul>
        </div><!-- /.nav-collapse -->
        <div class="navbar-collapse collapse navbar-responsive-collapse">
          <ul class="nav navbar-nav">
          </ul>
        </div><!-- /.nav-collapse -->
      </div>
    </div>
</div>

    <div class="container theme-showcase" role="main">


		<!-- All Rule Providers -->
		<div class="panel panel-primary">
			<#list getAllRuleProviders() as ruleProvider>
				<div class="panel-heading">
				    <h3 class="panel-title">${ruleProvider.ID}</h3>
				    Phase: ${ruleProvider.phase?replace("_", " ")?capitalize}
				</div>
				<table class="table table-striped table-bordered">
				  	<tr>
			    		<th>Rule</th>			    		
			    		<th>Statistics</th>
			    		<th>Executed?</th>
			    		<th>Failed?</th>
			    		<th>Failure Cause</th>
		  			</tr>
			  		<#list getRuleExecutionResults(ruleProvider) as ruleExecutionInfo>
						<#if ruleExecutionInfo??>
						<tr>
							<td>
									<a name="${ruleExecutionInfo.rule.id}" class="anchor"></a>
									<span style="white-space: pre">${formatRule(ruleExecutionInfo.rule)}</span>
							</td>
							<td>
								<div>Vertices Created: ${ruleExecutionInfo.vertexIDsAdded?size}</div>
								<div>Edges Created: ${ruleExecutionInfo.edgeIDsAdded?size}</div>
								<div>Vertices Removed: ${ruleExecutionInfo.vertexIDsRemoved?size}</div>
								<div>Edges Removed: ${ruleExecutionInfo.edgeIDsRemoved?size}</div>
							</td>					
							<td>
								${ruleExecutionInfo.executed?string("yes", "no")}
							</td>
							<td>
								${ruleExecutionInfo.failed?string("yes", "no")}
							</td>
							<td>
								<#if ruleExecutionInfo.failureCause?? && ruleExecutionInfo.failureCause.message??>
									${ruleExecutionInfo.failureCause.message}
								</#if>
							</td>
						</tr>
						<#else>
							<tr>
								<td></td>
								<td></td>
								<td></td>
								<td></td>
								<td></td>
								<td></td>
							</tr>
						</#if>
					</#list>
	    		</table>
	    	</#list>
		</div>

       
    </div> <!-- /container -->


    <script src="resources/js/jquery-1.10.1.min.js"></script>
    
    <script src="resources/libraries/flot/jquery.flot.min.js"></script>
    <script src="resources/libraries/flot/jquery.flot.pie.min.js"></script>
    
    <script src="resources/js/bootstrap.min.js"></script>
  </body>
</html>