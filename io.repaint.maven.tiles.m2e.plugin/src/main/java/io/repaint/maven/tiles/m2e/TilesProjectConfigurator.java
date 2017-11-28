//
// TilesProjectConfigurator.java
//
// Copyright (C) 2017
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package io.repaint.maven.tiles.m2e;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.configurator.AbstractProjectConfigurator;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;

/**
 * @author Erwin
 */
public class TilesProjectConfigurator extends AbstractProjectConfigurator {

	@Override
	public void configure(ProjectConfigurationRequest request, IProgressMonitor monitor) throws CoreException {
		MavenProject mavenProject = request.getMavenProject();

		// search for parents that are found in the workspace and are tile projects
		final IProject project = request.getProject();
		List<IProject> referencedProjects =
				new ArrayList<IProject>(Arrays.asList(project.getDescription().getReferencedProjects()));

		boolean modified = false;

		String appliedTiles = (String) mavenProject.getProperties().get(".applied-tiles");
		if (appliedTiles != null) {
			for (String tile : appliedTiles.split(",")) {
				String[] tileGAV = tile.split(":");
				IMavenProjectFacade projectFacade =
						MavenPlugin.getMavenProjectRegistry().getMavenProject(tileGAV[0], tileGAV[1], tileGAV[2]);
				if (projectFacade != null && "tile".equals(projectFacade.getPackaging())) {
					// add a project reference
					if (!referencedProjects.contains(projectFacade.getProject())) {
						referencedProjects.add(projectFacade.getProject());
						modified = true;
					}
				}
			}
		}

		if (modified) {
			final IProject[] refs = referencedProjects.toArray(new IProject[referencedProjects.size()]);
			IProjectDescription description = project.getDescription();
			description.setReferencedProjects(refs);
			project.setDescription(description, monitor);
		}
	}
}
